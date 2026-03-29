package org.openmarkov.annotation_processing.localization_bindings;

import org.jetbrains.annotations.NotNull;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.IOException;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

/**
 * Parses XML localization files and generates Java class definitions representing
 * the hierarchical structure of localization keys. Each XML element with a {@code value}
 * attribute becomes a nested class with a {@code stringify} method.
 */
class XMLConstantsParser {
    
    private static final String FIRST_ELEMENT_TO_IGNORE = "properties";
    private static final Pattern NAMED_PARAMETER_REGEX = Pattern.compile("(?x)" +
                                                                                 "\\{" +
                                                                                 "\\s*(?<name>\\w+?)\\s*" +
                                                                                 "(,\\s*(?<format>\\w+?)\\s*)?" +
                                                                                 "(,\\s*(?<style>\\w+?)\\s*)?" +
                                                                                 "(?<unused>,\\w*?)?" +
                                                                                 "}");
    
    /**
     * Parses the given XML file and produces a list of top-level {@link ClassDefinition} objects
     * that mirror the element hierarchy of the file.
     *
     * @param bundleName           the resource bundle name used to look up strings at runtime
     * @param xmlFilePath          absolute path to the XML localization file
     * @param xmlElementsToAvoid   element names to skip during parsing
     * @return a list of top-level class definitions representing the parsed XML structure
     */
    public static List<ClassDefinition> parseFiles(String bundleName, String xmlFilePath,
                                                   Set<@NotNull String> xmlElementsToAvoid) throws SAXException, IOException, ParserConfigurationException {
        SAXParser saxParser= SAXParserFactory.newInstance().newSAXParser();
        var endPointClasses = new ArrayList<PropertyAndValue>();
        BiConsumer<Stack<String>, String> onFindPropertyWithValue = (elements, value) -> {
            Stream<String> elementsStream = elements.stream();
            Optional<String> firstElement = elements.stream().findFirst();
            if (firstElement.isPresent() && firstElement.get().equals(XMLConstantsParser.FIRST_ELEMENT_TO_IGNORE)) {
                elementsStream = elementsStream.skip(1);
            }
            endPointClasses.add(new PropertyAndValue(elementsStream.toList(), value));
        };
        
        saxParser.parse(xmlFilePath, new XMLDocumentParser(onFindPropertyWithValue, xmlElementsToAvoid));
        
        var checkedIntermediaryClasses = endPointClasses
                .stream()
                .map(propertyAndValue -> String.join(".", propertyAndValue.path))
                .collect(Collectors.toSet());
        
        var intermediaryClasses = new ArrayList<List<String>>();
        endPointClasses.forEach(propertyAndValue -> {
            var endPointClassPath = propertyAndValue.path;
            IntStream
                    .range(1, endPointClassPath.size())
                    .mapToObj(subSize -> endPointClassPath.subList(0, subSize))
                    .forEach(sublist -> {
                        var pathAsString = String.join(".", sublist);
                        if (!checkedIntermediaryClasses.contains(pathAsString)) {
                            checkedIntermediaryClasses.add(pathAsString);
                            intermediaryClasses.add(sublist);
                        }
                    });
        });
        
        var intermediaryClassesDefinition = intermediaryClasses.stream().map(intermediaryClass ->
                                                                                     new ClassDefinition(intermediaryClass, "public static final class " + correctClassName(intermediaryClass.get(intermediaryClass.size() - 1))));
        var endPointClassesDefinition = endPointClasses
                .stream()
                .map(endPointClass -> {
                         
                         var stringParameters = XMLConstantsParser.extractParameterNames(endPointClass.value);
                         String stringifyFunction;
                         String getString = "org.openmarkov.core.localize.StringDatabase.getUniqueInstance().getString(\"" + bundleName + "\", \"" + String.join(".", endPointClass.path) + "\")";
                         if (stringParameters.isEmpty()) {
                             stringifyFunction = "public static String stringify() { return " + getString + "; } ";
                         } else {
                             var functionParameters = stringParameters.stream()
                                                                      .map(parameter -> "Object v" + parameter)
                                                                      .collect(Collectors.joining(","));
                             var createEntries = stringParameters.stream()
                                                                 .map(parameter -> "java.util.Map.entry(\"" + parameter + "\", v" + parameter + ")")
                                                                 .collect(Collectors.joining(", "));
                             stringifyFunction = "public static String stringify(" + functionParameters + ") {" +
                                     "return StringFormat.apply(" + getString + ", java.util.Map.ofEntries(" + createEntries + "));" +
                                     "}";
                         }
                         
                         return new ClassDefinition(endPointClass.path,
                                                    "public static final class " + correctClassName(endPointClass.path.get(endPointClass.path.size() - 1)),
                                                    stringifyFunction);
                         
                     }
                );
        
        var userClasses = new HashMap<String, ClassDefinition>();
        Stream.concat(intermediaryClassesDefinition, endPointClassesDefinition)
              .sorted(Comparator.comparingInt(definition -> definition.path.size()))
              .forEach(classDefinition -> {
                  String path = String.join(".", classDefinition.path);
                  userClasses.put(path, classDefinition);
                  if (classDefinition.path.size() > 1) {
                      var parentPath = classDefinition.path.stream().limit(classDefinition.path.size() - 1)
                                                           .collect(Collectors.joining("."));
                      userClasses.get(parentPath).subClasses.add(classDefinition);
                  }
              });
        List<ClassDefinition> classContents = new ArrayList<>(userClasses.entrySet().stream()
                                                                         .filter(entry -> !entry.getKey().contains("."))
                                                                         .map(Map.Entry::getValue)
                                                                         .toList());
        var parentTopClass = classContents.stream()
                                          .filter((topLevelClass) -> topLevelClass.path.get(0).equals(bundleName))
                                          .findFirst();
        parentTopClass.ifPresent(topClass -> {
            classContents.remove(topClass);
            classContents.addAll(topClass.subClasses());
        });
        
        return classContents;
        
        
    }
    
    private static String correctClassName(String className) {
        return className.replace(".", "_").replace("-", "_");
    }
    
    /**
     * Gets the {@code arguments} names of a {@code pattern} as it is done in StringFormat or org.openmarkov.core.
     *
     * @param pattern the message pattern containing named placeholders
     * @return the {@code arguments} names of a {@code pattern}.
     */
    public static List<String> extractParameterNames(CharSequence pattern) {
        var alreadyFoundParameters = new HashSet<String>();
        return NAMED_PARAMETER_REGEX
                .matcher(pattern)
                .results()
                .map(match -> match.group(1))
                .filter(alreadyFoundParameters::add)
                .toList();
    }
    
    /**
     * Represents a generated Java class definition with its hierarchical path, class header,
     * body contents, and nested sub-classes.
     */
    public record ClassDefinition(List<String> path, String classDefinition, String classContents,
                                  ArrayList<ClassDefinition> subClasses) {
        
        public ClassDefinition(List<String> path, String classDefinition, String classContents) {
            this(path, classDefinition, classContents, new ArrayList<>());
        }
        
        public ClassDefinition(List<String> path, String classDefinition) {
            this(path, classDefinition, "", new ArrayList<>());
        }
        
        /**
         * Adds all given sub-class definitions as children of this class.
         *
         * @param subClasses the sub-class definitions to add
         */
        public void addSubClasses(Collection<ClassDefinition> subClasses) {
            this.subClasses.addAll(subClasses);
        }
        
        @Override public String toString() {
            String subClassesContentsSeparated = subClasses
                    .stream()
                    .map(Objects::toString)
                    .collect(Collectors.joining(" "));
            return classDefinition + " { \n" + classContents + " " + subClassesContentsSeparated + "\n }";
        }
    }
    
    private record PropertyAndValue(List<String> path, String value) {
    }
    
    /**
     * SAX handler that walks the XML document, tracking the element path and invoking a
     * callback whenever an element with a {@code value} attribute is found.
     */
    public static class XMLDocumentParser extends org.xml.sax.helpers.DefaultHandler {
        
        final Stack<String> elementsPath;
        
        final BiConsumer<Stack<String>, String> onFindPropertyWithValue;
        final Set<String> elementsToAvoid;
        
        HashMap<String, Integer> currentlyAvoidingElements = new HashMap<>();
        private boolean processingIsEnabled = false;
        
        
        /**
         * Creates a new parser with the given callback and set of elements to skip.
         *
         * @param onFindPropertyWithValue callback invoked with the element path and value
         * @param elementsToAvoid         element names whose subtrees should be ignored
         */
        public XMLDocumentParser(BiConsumer<Stack<String>, String> onFindPropertyWithValue, Set<String> elementsToAvoid) {
            this.elementsToAvoid = elementsToAvoid;
            this.elementsPath = new Stack<>();
            this.onFindPropertyWithValue = onFindPropertyWithValue;
        }
        
        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
            super.startElement(uri, localName, qName, attributes);
            if (this.elementsPath.isEmpty()) {
                this.processingIsEnabled = !"ClassLocalizations".equals(qName);
            }
            this.elementsPath.push(qName);
            if (!this.processingIsEnabled) {
                return;
            }
            
            if (this.elementsToAvoid.contains(qName)) {
                if (!this.currentlyAvoidingElements.containsKey(qName)) {
                    this.currentlyAvoidingElements.put(qName, 0);
                }
                this.currentlyAvoidingElements.put(qName, this.currentlyAvoidingElements.get(qName) + 1);
            }
            
            if (!this.currentlyAvoidingElements.isEmpty()) {
                return;
            }
            var value = attributes.getValue("value");
            if (value != null) {
                this.onFindPropertyWithValue.accept(this.elementsPath, value);
            }
        }
        
        @Override public void endElement(String uri, String localName, String qName) throws SAXException {
            if (this.currentlyAvoidingElements.containsKey(qName)) {
                int newAvoidedAmount = this.currentlyAvoidingElements.get(qName) - 1;
                if (newAvoidedAmount > 0) {
                    this.currentlyAvoidingElements.put(qName, newAvoidedAmount);
                } else {
                    this.currentlyAvoidingElements.remove(qName);
                }
            }
            super.endElement(uri, localName, qName);
            this.elementsPath.pop();
        }
    }
    
}