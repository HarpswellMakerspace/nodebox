/*
 * This file is part of NodeBox.
 *
 * Copyright (C) 2008 Frederik De Bleser (frederik@pandora.be)
 *
 * NodeBox is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NodeBox is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NodeBox. If not, see <http://www.gnu.org/licenses/>.
 */
package net.nodebox.node;

import org.xml.sax.InputSource;

import javax.swing.event.EventListenerList;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EventListener;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Network extends Node {

    /**
     * A list of all the nodes in this network.
     */
    private HashMap<String, Node> nodes = new HashMap<String, Node>();

    /**
     * The node being rendered in this network.
     */
    private Node renderedNode = null;

    /**
     * The event listeners registered to listen to this network.
     */
    private EventListenerList listeners = new EventListenerList();

    private static Logger logger = Logger.getLogger("net.nodebox.node.Network");

    //// Constructors ////

    public Network(NodeType nodeType) {
        super(nodeType);
    }

    //// Container operations ////

    public boolean isEmpty() {
        return nodes.isEmpty();
    }

    public int size() {
        return nodes.size();
    }

    public void add(Node node) {
        assert (node != null);
        if (contains(node)) {
            return;
        }
        if (contains(node.getName())) {
            throw new InvalidNameException(this, node.getName(), "There is already a node named \"" + node.getName() + "\" in network " + getAbsolutePath());
        }
        node._setNetwork(this);
        nodes.put(node.getName(), node);
        fireNodeAdded(node);
    }

    public Node create(NodeType nodeType) {
        Node newNode = nodeType.createNode();
        setUniqueNodeName(newNode);
        add(newNode);
        return newNode;
    }

    public boolean remove(Node node) {
        assert (node != null);
        if (!contains(node)) {
            return false;
        }
        node.markDirty();
        // TODO: disconnect node from the old network.
        node.disconnect();
        nodes.remove(node.getName());
        if (node == renderedNode) {
            setRenderedNode(null);
        }
        fireNodeRemoved(node);
        return true;
    }

    public boolean contains(Node node) {
        return nodes.containsValue(node);
    }

    public boolean contains(String nodeName) {
        return nodes.containsKey(nodeName);
    }

    public Node getNode(String nodeName) {
        if (!contains(nodeName)) {
            throw new NotFoundException(this, nodeName, "Could not find node '" + nodeName + "' in network " + getAbsolutePath());
        }
        return nodes.get(nodeName);
    }

    public Collection<Node> getNodes() {
        return new ArrayList<Node>(nodes.values());
    }

    //// Naming operations ////

    /**
     * Changes the name of the given node to one that is unique within this network.
     * <p/>
     * Note that the node doesn't have to be in the network.
     *
     * @param node the node
     * @return the unique node name
     */
    public String setUniqueNodeName(Node node) {
        int counter = 1;
        while (true) {
            String suggestedName = node.getDefaultName() + counter;
            if (!contains(suggestedName)) {
                // We don't use rename here, since it assumes the node will be in 
                // this network.
                node.setName(suggestedName);
                return suggestedName;
            }
            ++counter;
        }
    }

    public boolean rename(Node node, String newName) {
        assert (contains(node));
        if (node.getName().equals(newName)) {
            return true;
        }
        if (contains(newName)) {
            return false;
        }
        nodes.remove(node.getName());
        node._setName(newName);
        nodes.put(newName, node);
        fireNodeChanged(node);
        return true;
    }

    //// Rendered node ////

    public Node getRenderedNode() {
        return renderedNode;
    }

    public void setRenderedNode(Node renderedNode) {
        if (renderedNode != null && !contains(renderedNode)) {
            throw new NotFoundException(this, renderedNode.getName(), "Node '" + renderedNode.getAbsolutePath() + "' is not in this network (" + getAbsolutePath() + ")");
        }
        if (this.renderedNode == renderedNode) return;
        this.renderedNode = renderedNode;
        markDirty();
        fireRenderedNodeChanged(renderedNode);
    }

    //// Processing ////

    @Override
    public void markDirty() {
        if (isDirty()) return;
        super.markDirty();
        fireNetworkDirty();
    }

    @Override
    public boolean update(ProcessingContext ctx) {
        boolean success = super.update(ctx);
        fireNetworkUpdated();
        return success;
    }

    protected boolean updateRenderedNode(ProcessingContext ctx) {
        if (renderedNode == null) {
            addError("No node to render");
            return false;
        }
        assert (contains(renderedNode));
        return renderedNode.update(ctx);
    }

    @Override
    public boolean process(ProcessingContext ctx) {
        // TODO: this method does NOT get overridden by the different network types!
        boolean success = updateRenderedNode(ctx);
        if (success) {
            setOutputValue(renderedNode.getOutputValue());
        } else {
            // If there is nothing to render, set the output to a sane default.
            getOutputParameter().revertToDefault();
        }
        return success;
    }

    //// Cycle detection ////

    public boolean containsCycles() {
        return false;
    }

    //// Event handling ////

    public void addNetworkEventListener(NetworkEventListener l) {
        listeners.add(NetworkEventListener.class, l);
    }

    public void removeNetworkEventListener(NetworkEventListener l) {
        listeners.remove(NetworkEventListener.class, l);
    }

    public void fireNodeAdded(Node node) {
        if (listeners == null) return;
        for (EventListener l : listeners.getListeners(NetworkEventListener.class))
            ((NetworkEventListener) l).nodeAdded(this, node);
    }

    public void fireNodeRemoved(Node node) {
        if (listeners == null) return;
        for (EventListener l : listeners.getListeners(NetworkEventListener.class))
            ((NetworkEventListener) l).nodeRemoved(this, node);
    }

    public void fireConnectionAdded(Connection connection) {
        if (listeners == null) return;
        for (EventListener l : listeners.getListeners(NetworkEventListener.class))
            ((NetworkEventListener) l).connectionAdded(this, connection);
    }

    public void fireConnectionRemoved(Connection connection) {
        if (listeners == null) return;
        for (EventListener l : listeners.getListeners(NetworkEventListener.class))
            ((NetworkEventListener) l).connectionRemoved(this, connection);
    }

    public void fireRenderedNodeChanged(Node node) {
        if (listeners == null) return;
        for (EventListener l : listeners.getListeners(NetworkEventListener.class))
            ((NetworkEventListener) l).renderedNodeChanged(this, node);
    }

    public void fireNodeChanged(Node node) {
        if (listeners == null) return;
        for (EventListener l : listeners.getListeners(NetworkEventListener.class))
            ((NetworkEventListener) l).nodeChanged(this, node);
    }

    public void addNetworkDataListener(NetworkDataListener l) {
        listeners.add(NetworkDataListener.class, l);
    }

    public void removeNetworkDataListener(NetworkDataListener l) {
        listeners.remove(NetworkDataListener.class, l);
    }

    public void fireNetworkDirty() {
        if (listeners == null) return;
        for (EventListener l : listeners.getListeners(NetworkDataListener.class))
            ((NetworkDataListener) l).networkDirty(this);
    }

    public void fireNetworkUpdated() {
        if (listeners == null) return;
        for (EventListener l : listeners.getListeners(NetworkDataListener.class))
            ((NetworkDataListener) l).networkUpdated(this);
    }

    //// Persistence ////

    public String toXml() {
        StringBuffer xml = new StringBuffer();
        // Build the header
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        xml.append("<ndbx type=\"file\" formatVersion=\"0.8\">\n");
        toXml(xml, "  ");
        xml.append("</ndbx>");
        return xml.toString();
    }

    /**
     * Converts the data structure to xml. The xml String is appended to the given StringBuffer.
     *
     * @param xml    the StringBuffer to use when appending.
     * @param spaces the indentation
     * @see Network#toXml for returning the Network as a full xml document
     */
    public void toXml(StringBuffer xml, String spaces) {
        // Build the node
        xml.append(spaces);
        xml.append("<network");
        xml.append(" name=\"").append(getName()).append("\"");
        xml.append(" type=\"").append(getNodeType().getIdentifier()).append("\"");
        xml.append(" version=\"").append(getNodeType().getVersionAsString()).append("\"");
        xml.append(" x=\"").append(getX()).append("\"");
        xml.append(" y=\"").append(getY()).append("\"");
        xml.append(">\n");

        // Create the data
        dataToXml(xml, spaces);

        // Include all vertices
        for (Node n : getNodes()) {
            n.toXml(xml, spaces + "  ");
        }

        // Include all edges
        for (Node n : getNodes()) {
            for (Connection c : n.getOutputConnections()) {
                c.toXml(xml, spaces + "  ");
            }
        }

        // End the node
        xml.append(spaces).append("</network>\n");
    }

    public static Network load(NodeManager nodeManager, String s) throws RuntimeException {
        StringReader reader = new StringReader(s);
        return load(nodeManager, new InputSource(reader));
    }

    public static Network load(NodeManager nodeManager, File file) throws RuntimeException {
        // Load the document
        FileInputStream fis;
        try {
            fis = new FileInputStream(file);
        } catch (FileNotFoundException e) {
            logger.log(Level.SEVERE, "Could not read file " + file, e);
            throw new RuntimeException("Could not read file " + file, e);
        }
        return load(nodeManager, new InputSource(fis));
    }

    private static Network load(NodeManager nodeManager, InputSource source) throws RuntimeException {
        // Setup the parser
        SAXParserFactory factory = SAXParserFactory.newInstance();
        // The next lines make sure that the SAX parser doesn't try to validate the document,
        // or tries to load in external DTDs (such as those from W3). Non-parsing means you
        // don't need an internet connection to use the program, and speeds up loading the
        // document massively.
        try {
            factory.setFeature("http://xml.org/sax/features/validation", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Parsing feature not supported.", e);
            throw new RuntimeException("Parsing feature not supported.", e);
        }
        SAXParser parser;
        try {
            parser = factory.newSAXParser();
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Could not create parser.", e);
            throw new RuntimeException("Could not create parser.", e);
        }

        // Parse the document
        XmlHandler handler = new XmlHandler(nodeManager);
        try {
            parser.parse(source, handler);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Error during parsing.", e);
            throw new RuntimeException("Error during parsing.", e);
        }
        return handler.getNetwork();

    }


}