/* (C)2023 */
package org.transitclock.config;

import lombok.extern.slf4j.Slf4j;
import org.transitclock.config.ConfigValue.ConfigParamException;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The ConfigFile class provides a way of handling parameters that can be updated without having to
 * restart the application. The parameters can be access efficiently so that they can be accessed in
 * loops that are frequently executed. This class is useful for parameters that are system wide and
 * often have a default. Such params are not best stored in a database because with a db it can be
 * harder to see what all the values are or to copy the values from one project to another. It can
 * also be harder to keep track of changes when such params are in a database.
 *
 * <p>An additional goal of Config is for all params to be processed when the config file is read,
 * both at startup and when file is reread. This is important because then if there are any
 * processing problems those problems are noticed and must be dealt with immediately (instead of
 * them occurring in an lazy fashion, perhaps when no one is available to deal with them). But this
 * goal would mean that the params would need to be defined in config files so they would all be
 * dealt with at startup. For many situations want the params to be defined where they are used
 * since that would make them easier to support. But then they wouldn't get initialized until the
 * Java class is actually accessed.
 *
 * <p>For each parameter configured in the file the property is written as a system property if that
 * system property is not yet defined. This way all parameters are available as system properties,
 * even non-transitime ones such as for hibernate or logback. Java properties set on the command
 * line take precedence since the properties are not overwritten if already set.
 *
 * <p>The params are configured in an XML or a properties file. The member processConfig() first
 * process a file named transitclock.properties if one exists in the classpath. Then files specified
 * by the Java system property transitclock.configFiles . If the file has a .xml suffix then it will
 * be processed as XML. Otherwise it will be processed as a regular properties file. Multiple config
 * files can be used. Each XML file should have a corresponding configuration class such as
 * CoreConfig.java. In the XXXConfig.java class the params are specified along with default values.
 *
 * <p>Multiple threads can read parameters simultaneously. The threads should call readLock() if it
 * is important to update the parameters in a consistent way, such as when more than a single
 * parameter are interrelated.
 *
 * <p>Logback error logging is not used in this class so that logback can be not accessed until
 * after the configuration is processed. In this way logback can be configured using a configuration
 * file to set any logback Java system properties such as logback.configurationFile .
 *
 * @author SkiBu Smith
 */
@Slf4j
public class ConfigFileReader {
    /**
     * Need to be able to synchronize reads and writes. Since this class is static need static
     * synchronization objects. Using a ReentrantReadWriteLock so that multiple threads with reads
     * can happen simultaneously. This works well because writing/updating data will be very
     * infrequent.
     */
    private static final ReentrantReadWriteLock rwl = new ReentrantReadWriteLock();

    private static final Lock readLock = rwl.readLock();
    private static final Lock writeLock = rwl.writeLock();

    /**
     * An exception for when a parameter is being read in
     *
     * @author SkiBu Smith
     */
    public static class ConfigException extends Exception {
        private ConfigException(Exception e) {
            super(e);
        }

        private ConfigException(String msg) {
            super(msg);
        }
    }

    /**
     * processChildren() is called recursively to process data from an xml file.
     *
     * @param nodeList The data from the XML file that was processed by the DOM XML processor
     * @param names For keeping track of the full hierarchical parameter name such as
     *     "transitest.predictor.maxDistance".
     */
    private static void processChildren(NodeList nodeList, List<String> names) {
        for (int i = 0; i < nodeList.getLength(); ++i) {
            Node node = nodeList.item(i);

            // Only need to deal with ELEMENT_NODES. Otherwise get
            // extraneous info.
            if (node.getNodeType() != Node.ELEMENT_NODE) continue;

            String nodeName = node.getNodeName();
            names.add(nodeName);

            // If there are child nodes handle them recursively.
            if (hasChildNodesThatAreElements(node)) {
                NodeList childNodeList = node.getChildNodes();
                processChildren(childNodeList, names);
            } else {
                // Determine full name of parameter by appending node
                // names together, separated by a period. So get something
                // like "transitclock.predictor.radius".
                StringBuilder propertyNameBuilder = new StringBuilder();
                for (int j = 0; j < names.size(); ++j) {
                    String name = names.get(j);
                    propertyNameBuilder.append(name);
                    if (j != names.size() - 1) propertyNameBuilder.append(".");
                }
                String propertyName = propertyNameBuilder.toString();

                // Determine value specified in xml file
                String value = node.getTextContent().trim();

                // If the property hasn't yet been set then set the configured
                // property as a system property so it can be read in using
                // ConfigValue class. The reason only do this if the system
                // property hasn't been set yet is so that parameters set on
                // the system command line will take precedence.
                if (System.getProperty(propertyName) == null) System.setProperty(propertyName, value);
            }

            // Done with this part of the tree so take the current node name
            // from the list
            names.remove(names.size() - 1);
        }
    }

    /**
     * Determines whether a node has valid child elements. Filters out ones that are not a real
     * ELEMENT_NODE.
     *
     * @param node
     * @return
     */
    private static boolean hasChildNodesThatAreElements(Node node) {
        NodeList childNodeList = node.getChildNodes();
        for (int i = 0; i < childNodeList.getLength(); ++i) {
            Node childNode = childNodeList.item(i);

            // Only need to deal with ELEMENT_NODES. Otherwise get extraneous
            // info.
            if (childNode.getNodeType() == Node.ELEMENT_NODE) return true;
        }

        return false;
    }

    /**
     * Reads the specified XML file and stores the results into a HashMap configFileData. The file
     * is expected to be very simple. Attributes are ignored. Only values are used. The keys for the
     * resulting HashMap are based on the XML tags and their nesting. So if one is setting a value
     * of 75 to a param with a key transitclock.predictor.allowableDistanceFromPath the XML would
     * look like: {@code <transitime> <predictor> <allowableDistanceFromPath> 75.0
     * </allowableDistanceFromPath> </predictor> </transitime> }
     *
     * @param fileName
     */
    public static void readXmlConfigFile(String fileName) throws ConfigException {
        File xmlFile = new File(fileName);
        DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
        Document doc;
        try {
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            doc = dBuilder.parse(xmlFile);
        } catch (Exception e) {
            System.err.println("Error parasing XML file \"" + fileName + "\" : " + e.getMessage());

            throw new ConfigException(e);
        }

        // So text strings separated by newlines are not
        // considered separate text nodes
        doc.getDocumentElement().normalize();

        // For keeping track of names
        List<String> names = new ArrayList<String>();
        // Where to start in hierarchy when recursively calling processChild()
        NodeList nodeList = doc.getChildNodes();
        processChildren(nodeList, names);
    }

    /**
     * Reads a Java properties file and stores all the properties as system properties if they
     * haven't yet been set.
     *
     * @param inputStream The properties file
     */
    private static void readPropertiesConfigFile(InputStream inputStream) {
        try {
            // Read the properties config file
            Properties properties = new Properties();
            properties.load(inputStream);
            Pattern pattern = Pattern.compile("\\$\\{(\\w+)(?::(.*?))?\\}");

            // For each property in the config file...
            for (String propertyName : properties.stringPropertyNames()) {
                // If property not set yet then set it now
                if (System.getProperty(propertyName) == null) {
                    String value = properties.getProperty(propertyName);

                    // Experimental : Replace Placeholders with ENV vars
                    // Helps with dynamic configuration on Docker
                    Matcher matcher = pattern.matcher(value);

                    while (matcher.find()) {
                        String varName = matcher.group(1);
                        String defaultValue = matcher.group(2);
                        String envValue = System.getenv(varName);

                        if (envValue != null) {
                            // Replace the placeholder with the environment variable's value
                            value = value.replace("${" + varName + (defaultValue != null ? ":" + defaultValue : "") + "}", envValue);
                        } else if (defaultValue != null) {
                            // If the environment variable is not found but a default value is specified, use the default value
                            value = value.replace("${" + varName + ":" + defaultValue + "}", defaultValue);
                        }
                    }

                    // Set the system property with the value
                    System.setProperty(propertyName, value);

                    logger.debug("Setting property name {} to value {}", propertyName, value);
                }
            }
        } catch (IOException e) {
            System.err.println("Exception occurred reading in properties file. " + e.getMessage());
        }
    }

    /**
     * Reads a Java properties file and stores all the properties as system properties if they
     * haven't yet been set.
     *
     * @param fileName Name of properties file
     */
    private static void readPropertiesConfigFile(String fileName) {
        try (FileInputStream file = new FileInputStream(fileName)) {
            readPropertiesConfigFile(file);
        } catch (IOException e) {
            logger.error("Exception occurred reading in fileName " + fileName + " . " + e.getMessage(), e);
        }
    }

    /**
     * When reading config params should lock up the system so that data is
     * consistent. This is a good idea because even though each individual param
     * is atomic there could be situations where multiple interdependent params
     * are being used and don't want a write to change them between access.
     *
     * The expectation is that the program is event driven. When an event is
     * processed then a single call to readLock() and readUnlock() can protect
     * the entire code where parameters might be read. The intent is not to read
     * lock each single access of data.
     *
     * Every time readLock() is called there needs to be a corresponding call to
     * readUnlock(). Since this important the readUnlock() should be in a
     * finally block. For example: {@code try { readLock(); ...; // Read access
     * the data } finally { readUnlock(); }
     */
    public static void readLock() {
        readLock.lock();
    }

    /**
     * Every time readLock() is called there needs to be a call to readUnlock(). Since this is
     * important the readUnlock() should be called in a finally block. See readLock() for details.
     */
    public static void readUnlock() {
        readLock.unlock();
    }

    /**
     * Processes specified config file and overrides the config parameters accordingly. This way can
     * use an XML config file instead of -D Java properties to set configuration parameters.
     *
     * <p>Reads in data and stores it in the config objects so that it is programmatically
     * accessible. Does a write lock so that readers will only execute when data is not being
     * modified.
     *
     * @param fileName Name of the config file. If null then default values will be used for each
     *     parameter.
     * @throws ConfigException
     * @throws ConfigParamException
     */
    public static void processConfig(String fileName) throws ConfigException, ConfigParamException {
        // Don't want reading while writing so lock access
        writeLock.lock();

        logger.info("Processing configuration file " + fileName);

        try {
            // Read in the data from config file
            if (fileName != null)
                if (fileName.endsWith(".xml")) readXmlConfigFile(fileName);
                else readPropertiesConfigFile(fileName);
        } finally {
            // Make sure the write lock gets unlocked no matter what happens
            writeLock.unlock();
        }
    }

    /**
     * Process the configuration file specified by a file named transitclock.properties that is in
     * the classpath. The processes configuration files specified by the Java system property
     * transitclock.configFiles. The transitclock.configFiles property is a ";" separated list so
     * can specify multiple files. The files can be either in either XML or in Java properties
     * format.
     *
     * <p>All parameters found will be set as system properties unless the system property was
     * already set. This way Java system properties set on the command line have precedence.
     *
     * <p>By making all read parameters Java system properties they can be used for external
     * libraries such as for specifying hibernate or logback config files.
     */
    public static void processConfig() {
        // Determine from the Java system property transitclock.configFiles
        // the configuration files to be read
        String configFilesStr = System.getProperty("transitclock.configFiles");

        if (configFilesStr != null) {
            String[] configFiles = configFilesStr.split(";");
            for (String fileName : configFiles) {
                try {
                    // Read in and process the config file
                    processConfig(fileName);
                } catch (ConfigException | ConfigParamException e) {
                    e.printStackTrace();
                }
            }
        }

        // If the file transitclock.properties exists in the classpath then
        // process it. This is done after the transitclock.configFiles files
        // are read in so that they have precedence over the file found
        // in the classpath.
        String defaultPropertiesFileName = "transitclock.properties";
        InputStream propertiesInput =
                ConfigFileReader.class.getClassLoader().getResourceAsStream(defaultPropertiesFileName);
        if (propertiesInput != null) {
            readPropertiesConfigFile(propertiesInput);
        }
    }
}
