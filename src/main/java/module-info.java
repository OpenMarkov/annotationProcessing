import org.openmarkov.annotation_processing.localization_bindings.BindLocalizationsProcessor;

module org.openmarkov.annotation_processing {
    requires org.jetbrains.annotations;
    requires java.compiler;
    requires java.xml;
    requires com.google.auto.service;
    
    exports org.openmarkov.annotation_processing.localization_bindings;
    
    provides javax.annotation.processing.Processor
            with BindLocalizationsProcessor;
}
