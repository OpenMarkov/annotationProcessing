package org.openmarkov.annotation_processing.localization_bindings;

import java.lang.annotation.*;


/**
 * When attaching this annotation to a class, you can specify a list of XML files inside the 'resources' dir of your
 * project to create a binding every XML element containing a 'value' attribute.
 *
 * <p>Example of use:</p>
 *
 * Having a file in {@code src/main/resources/my_localization_files/Dialogs_en.xml} with these contents:
 *
 * <blockquote><pre>
 * {@code
 * <?xml version="1.0" encoding="UTF-8"?>
 * <properties>
 * 	<WrongNetType value="The {NetName} net is not a {DesiredNetType}, and this functionality is only available for {DesiredNetType}"/>
 * 	<AddOtherProperty value="Add another property">
 * 		<Name>
 *              <Title value="Add property"/>
 * 			 <Message value="Property name"/>
 *         </Name>
 *     </AddOtherProperty>
 * </properties>
 * }
 * </pre></blockquote><br>
 * We could create a binding to every XML element that has a value by attaching an {@link BindXML} annotation to a class
 * and indicating the path to the XML file whose binding we want to generate, in this case,
 * {@code my_localization_files/Dialogs_en.xml}:
 * <blockquote><pre>
 * {@code
 * package org.openmarkov.testBinding;
 *
 * @BindXML(filePath = "my_localization_files/Dialogs_en.xml")
 * public class Main { }
 * }
 * </pre></blockquote><br>
 * This automatically generates this big and boilerplate class for us:
 *
 * <blockquote><pre>
 * {@code
 * package org.openmarkov.testBinding;
 *
 * import org.openmarkov.core.stringformat.StringFormat;
 *
 * public final class Localize {
 *     public static final class WrongNetType {
 *         public static String stringify(Object NetName, Object DesiredNetType) {
 *             return StringFormat.apply(org.openmarkov.gui.localize.StringDatabase.getUniqueInstance()
 *                                                                                 .getString("WrongNetType"),
 *                                       java.util.Map.ofEntries(
 *                                               java.util.Map.entry("NetName", NetName),
 *                                               java.util.Map.entry("DesiredNetType", DesiredNetType)));
 *         }
 *     }
 *
 *     public static final class AddOtherProperty {
 *         public static String stringify() {
 *             return org.openmarkov.gui.localize.StringDatabase.getUniqueInstance().getString("AddOtherProperty");
 *         }
 *
 *         public static final class Name {
 *             public static final class Title {
 *                 public static String stringify() {
 *                     return org.openmarkov.gui.localize.StringDatabase.getUniqueInstance()
 *                                                                      .getString("AddOtherProperty.Name.Title");
 *                 }
 *             }
 *
 *             public static final class Message {
 *                 public static String stringify() {
 *                     return org.openmarkov.gui.localize.StringDatabase.getUniqueInstance()
 *                                                                      .getString("AddOtherProperty.Name.Message");
 *                 }
 *             }
 *         }
 *     }
 * }
 * }
 * </pre></blockquote><br>
 *
 * Take a look at how every xml element that had a value is now represented by a class, for example,
 * 'AddOtherProperty.Name.Title' is now represented by the hierarchy of a nested class, so we can find it accessing
 * {@code AddOtherProperty}, then {@code Name}, and then {@code Title} (This is basically
 * {@code AddOtherProperty.Name.Title}) and this class has a static {@code stringify} method we can call to
 * retrieve the string.
 * <p>
 * The case of the value for the element {@code WrongNetType} is a bit more peculiar, as in the XML there is two
 * arguments: NetType and DesiredNetType, so its {@code stringify} is actually {@code stringify(String NetType,
 * String DesiredNetType)}.
 * <p>
 * The following example code shows how we can use this in a real scenario, in this case, we print every value of the
 * XML:
 *
 * <blockquote><pre>
 * {@code
 * package org.openmarkov.testBinding;
 *
 * @BindXML(filePath = "my_localization_files/Dialogs_en.xml")
 * public class Main {
 *
 *     public static void main(String[] args) {
 *         System.out.println("Localize.AddOtherProperty = " +
 *                                    Localize.AddOtherProperty.stringify());
 *         System.out.println("Localize.AddOtherProperty.Name.Title = " +
 *                                    Localize.AddOtherProperty.Name.Title.stringify());
 *         System.out.println("Localize.AddOtherProperty.Name.Message = " +
 *                                    Localize.AddOtherProperty.Name.Message.stringify());
 *         System.out.println("Localize.WrongNetType = " +
 *                                    Localize.WrongNetType.stringify("MyNet", "Bayesian"));
 *     }
 * }
 * }
 * </pre></blockquote><br>
 *
 * And upon executing, this would be its results:
 *
 * <pre>{@code
 * Localize.AddOtherProperty = Add another property
 * Localize.AddOtherProperty.Name.Title = Add property
 * Localize.AddOtherProperty.Name.Message = Property name
 * Localize.WrongNetType = The MyNet net is not a Bayesian, and this functionality is only available for Bayesian
 * }</pre>
 *
 * @author jrico
 */
@Target(ElementType.TYPE)
@Repeatable(BindXMLRepetition.class)
@Retention(RetentionPolicy.SOURCE)
public @interface BindXML {
    
    /**
     * List of files and/or directories where XML files are, so bindings can be generated out of said XML files.
     * <p>
     * For directories, it will take all the XML files inside said directory, not applying a recursive search.
     *
     * @return A list of files and/or directories with XML from which the bindings are generated.
     */
    String[] filePath();
    
    /**
     * Specifies the method that processes the xml path to get the string.
     * <p>
     * By default, it calls the 'org.openmarkov.gui.localize.StringDatabase.getUniqueInstance().getString' to get a
     * string out of the path.
     * <p>
     * It can be set as empty to return the value of the XML element instead of processing its path.
     *
     * @return the method that processes the xml path to get the string.
     */
    String xmlPathToStringFunction() default "org.openmarkov.gui.localize.StringDatabase.getUniqueInstance().getString";
    
    /**
     * Specifies the package where the binding class will be located at.
     * <p>
     * Its name by default is 'Localize' if you don't specify it.
     *
     * @return the package where the binding class will be located at.
     */
    String inPackage() default "";
    
    /**
     * Specifies the name of the class where the bindings will be generated.
     * <p>
     * When left as empty, it uses the name of the binding file as the name for the generated class, which is the
     * default behavior.
     *
     *
     * @return the name of the class where the bindings will be generated.
     */
    String inBaseClass() default "";
    
    /**
     * When generating the bindings, it filters the files using this regex against their name.
     * <p>
     * By default, this is {@link BindXML#ENGLISH_FILE_NAME_REGEX}, meaning bindings are generated based on the
     * English localization files.
     * <p>
     * Note: This only applies to files indicated by *directory*, if you specify a xml file in
     * {@link BindXML#filePath()}, said file won't be checked against this pattern, but if you specify a directory in
     * {@link BindXML#filePath()}, it's contained files will be checked against this pattern.
     *
     * @return A regex filtering the names of the files whose bindings will be made.
     */
    String filterFileNameByRegex() default BindXML.ENGLISH_FILE_NAME_REGEX;
    
    /**
     * Pattern followed by english localization files.
     * <p>
     * This means english localization files always end on 'en.xml', such as 'Dialogs_en.xml'.
     */
    String ENGLISH_FILE_NAME_REGEX = "en\\.xml$";
    
    /**
     * Pattern followed by spanish localization files.
     * <p>
     * This means spanish localization files always end on 'es.xml', such as 'Dialogs_es.xml'.
     */
    String SPANISH_FILE_NAME_REGEX = "es\\.xml$";
}