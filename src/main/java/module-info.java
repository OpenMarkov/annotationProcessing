module org.openmarkov.annotation_processing {
    requires org.jetbrains.annotations;
    requires java.compiler;
    requires com.google.auto.service;
    requires java.xml;
    
    exports org.openmarkov.annotation_processing.localization_bindings;
    
    provides javax.annotation.processing.Processor
            with org.openmarkov.annotation_processing.localization_bindings.BindXMLProcessor;
}
