package org.openmarkov.annotation_processing.localization_bindings;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Allows {@link BindXML} to be repeated.
 *
 * @author jrico
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.SOURCE)
public @interface BindXMLRepetition {
    /**
     * The multiple repetitions of {@link BindXML}.
     *
     * @return multiple repetitions of {@link BindXML}.
     */
    BindXML[] value();
}

