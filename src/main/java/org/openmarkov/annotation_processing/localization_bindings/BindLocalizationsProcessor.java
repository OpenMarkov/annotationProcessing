package org.openmarkov.annotation_processing.localization_bindings;

import com.google.auto.service.AutoService;
import org.jetbrains.annotations.Nullable;
import org.xml.sax.SAXException;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.FileObject;
import javax.tools.JavaFileObject;
import javax.tools.StandardLocation;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.*;
import java.util.stream.Stream;

/**
 * Process classes annotated with {@link BindLocalizations} by generating a Binding class out of the specified resources.
 *
 * @author jrico
 */
@SupportedAnnotationTypes({
        "org.openmarkov.annotation_processing.localization_bindings.BindLocalizations",
        "org.openmarkov.annotation_processing.localization_bindings.BindLocalizationsRepetition"
})
@SupportedSourceVersion(SourceVersion.RELEASE_25)
@AutoService(Processor.class)
public class BindLocalizationsProcessor extends AbstractProcessor {
    
    public BindLocalizationsProcessor() {
        super();
    }
    
    /**
     * Trims and returns the {@code input} {@link String}, unless it is blank or null, in which case it trims and
     * returns the other {@code defaultString}.
     *
     * @return {@code input} trimmed, and if empty, {@code defaultString} trimmed.
     */
    private static String getStringOrDefault(String input, String defaultString) {
        if (input == null || input.isBlank())
            return defaultString.trim();
        return input.trim();
    }
    
    /**
     * Gathers {@link BindLocalizations} from every tagged element to create an XMLBinding class using
     * {@link BindLocalizationsProcessor#createBindingClass(BindLocalizations, Element)}.
     *
     * @return true
     */
    @Override
    public final boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        this.processingEnv.getMessager()
                          .printMessage(Diagnostic.Kind.NOTE, "- BindLocalizations processing round start -");

        
        annotations
                .stream()
                .map(roundEnv::getElementsAnnotatedWith)
                .flatMap(Collection::stream)
                .flatMap(element -> {
                    var singleAnnotation = element.getAnnotation(BindLocalizations.class);
                    if (singleAnnotation != null) return Stream.of(new BindingInformation(element, singleAnnotation));
                    var multipleAnnotations = element.getAnnotation(BindLocalizationsRepetition.class);
                    return Arrays.stream(multipleAnnotations.value())
                                 .map(annotation -> new BindingInformation(element, annotation));
                })
                .forEach(bindingInfo -> {
                    try {
                        this.createBindingClass(bindingInfo.definition, bindingInfo.annotatedElement);
                    } catch (IOException | SAXException | ParserConfigurationException ex) {
                        this.processingEnv.getMessager()
                                          .printMessage(Diagnostic.Kind.ERROR, "Could not create binding due to: " + ex, bindingInfo.annotatedElement);
                    }
                });
        this.processingEnv.getMessager()
                          .printMessage(Diagnostic.Kind.NOTE, "- BindLocalizations processing round end -");
        return true;
    }
    
    /**
     * Creates a binding class out of the {@code bindingInfo} and writes it as a source class.
     * <p>
     * If writing fails, it is ignored and just a note is written on the element, as this usually happens when the
     * project has already been built.
     *
     * @throws IOException When IO handling.
     * @throws SAXException When XML format is wrong.
     */
    @SuppressWarnings({"SpellCheckingInspection", "DuplicateStringLiteralInspection"})
    private void createBindingClass(BindLocalizations bindingInfo, Element element) throws IOException, SAXException, ParserConfigurationException {
        var defaultPackage = this.processingEnv.getElementUtils().getPackageOf(element).toString();
        var inPackage = BindLocalizationsProcessor.getStringOrDefault(bindingInfo.inPackage(), defaultPackage);
        var languageFilter = bindingInfo.filterFileNameByLanguage().fileTerminator();
        var filesSet = new HashSet<String>();
        for (var path : bindingInfo.filePath()) {
            var resource = this.callerResourcePathToFile(this.processingEnv, path, bindingInfo.fileIsDirectoryChild());
            if (resource == null)
                continue;
            var files = resource.isFile() ? List.of(resource)
                    : Arrays.stream(Objects.requireNonNull(resource.listFiles())).toList();
            for (var file : files) {
                boolean isMatchedFile = file.isFile() && file.getName().endsWith(languageFilter);
                if (!isMatchedFile || !file.getName().endsWith("xml"))
                    continue;
                filesSet.add(file.getAbsolutePath());
            }
        }
        this.processingEnv.getMessager()
                          .printMessage(Diagnostic.Kind.NOTE, "Processing BindLocalizations with files: " + filesSet);
        
        var localizationClasses = new ArrayList<XMLConstantsParser.ClassDefinition>(filesSet.size());
        for (String filepath : filesSet) {
            File file = new File(filepath);
            String bundleName = file.getName()
                                    .substring(0, file.getName()
                                                      .length() - languageFilter.length());
            var constantsClass = new XMLConstantsParser
                    .ClassDefinition(new ArrayList<>(), "public final class " + bundleName, "");
            var xmlSubclasses = XMLConstantsParser.parseFiles(bundleName, filepath, Set.of("BUNDLEFILE"));
            constantsClass.addSubClasses(xmlSubclasses);
            localizationClasses.add(constantsClass);
        }
        var constantsClass = new XMLConstantsParser
                .ClassDefinition(new ArrayList<>(), "package " + inPackage + ";" +
                "import org.openmarkov.core.stringformat.StringFormat;" +
                "public final class " + bindingInfo.inBaseClass(), "");
        constantsClass.addSubClasses(localizationClasses);
        JavaFileObject fileObject = this.processingEnv.getFiler()
                                                      .createSourceFile(inPackage + "." + bindingInfo.inBaseClass());
        try (Writer writer = fileObject.openWriter()) {
            writer.write(constantsClass.toString());
            this.processingEnv.getMessager()
                              .printMessage(Diagnostic.Kind.NOTE, "Binding class was successfully created");
        } catch (FilerException ex) {
            @SuppressWarnings("BooleanVariableAlwaysNegated")
            boolean isRecreateError = ex.getMessage().startsWith("Attempt to recreate a file");
            if (!isRecreateError) {
                throw ex;
            }
            this.processingEnv.getMessager()
                              .printMessage(Diagnostic.Kind.NOTE, "This class was trying to replace an already existing file: " + ex, element);
        }
    }
    
    /**
     * Returns an {@code Optional<File>} containing the file only if it exists.
     *
     * @return an {@code Optional<File>} containing the file only if it exists.
     */
    @SuppressWarnings("OverlyBroadCatchBlock")
    private @Nullable File callerResourcePathToFile(ProcessingEnvironment environment, CharSequence resourceRelativePath, boolean fileIsDirectoryChild) {
        this.processingEnv.getMessager()
                          .printMessage(Diagnostic.Kind.NOTE, "Resolving resource: " + resourceRelativePath);
        try {
            FileObject resource = environment
                    .getFiler()
                    .getResource(StandardLocation.CLASS_OUTPUT, "", resourceRelativePath);
            this.processingEnv.getMessager()
                              .printMessage(Diagnostic.Kind.NOTE, "Resource is file: " + resource.toUri());
            var file = new File(resource.toUri().toURL().getFile());
            if (!file.exists()) {
                this.processingEnv.getMessager()
                                  .printMessage(Diagnostic.Kind.NOTE, "This resource does not exists: " + file.getAbsolutePath() + ".");
                return null;
            }
            if (fileIsDirectoryChild) {
                file = file.getParentFile();
            }
            if (!file.exists()) {
                this.processingEnv.getMessager()
                                  .printMessage(Diagnostic.Kind.NOTE, "This resource does not exists: " + file.getAbsolutePath() + ".");
                return null;
            }
            return file;
        } catch (IOException ex) {
            this.processingEnv.getMessager()
                              .printMessage(Diagnostic.Kind.NOTE, "Could not resolve file " + resourceRelativePath);
            
            return null;
        }
    }
    
    /**
     * Simple struct for holding an {@code annotatedElement} that was annotated with {@link BindLocalizations}, and the own
     * {@link BindLocalizations} in the {@code definition} component.
     */
    private record BindingInformation(Element annotatedElement, BindLocalizations definition) {
    }
}