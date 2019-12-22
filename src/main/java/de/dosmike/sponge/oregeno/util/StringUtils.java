package de.dosmike.sponge.oregeno.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StringUtils extends org.apache.commons.lang3.StringUtils {
    /**
     * searches a substring in the input defined by the regex.
     * if found the result is a string array containing the reduced input and the stripped string.
     * otherwise the input is returned as single entry
     * @param regex the expression to search
     * @param input the string to search in
     * @return [ Stripped Input, (Optional) Match ]
     */
    public static String[] consume(String regex, String input) {
        return consume(Pattern.compile(regex), input);
    }
    /**
     * searches a substring in the input defined by the regex.
     * if found the result is a string array containing the reduced input and the stripped string.
     * otherwise the input is returned as single entry
     * @param regex the compiled pattern to search with
     * @param input the string to search in
     * @return [ Stripped Input, (Optional) Match ]
     */
    public static String[] consume(Pattern regex, String input) {
        Matcher m = regex.matcher(input);
        if (m.find()) {
            String b = m.group();
            return new String[] { m.replaceFirst(""), b } ;
        } else return new String[]{ input };
    }
}
