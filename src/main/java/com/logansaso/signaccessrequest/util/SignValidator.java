package com.logansaso.signaccessrequest.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

/**
 * Utility class for validating and working with C1 access request signs
 */
public class SignValidator {

    public static final String SIGN_PREFIX = "[c1-req]";

    /**
     * Checks if a component has blue text color
     */
    public static boolean isBlueText(Component component) {
        if (component == null) {
            return false;
        }

        TextColor color = component.color();
        return color != null && color.equals(NamedTextColor.BLUE);
    }

    /**
     * Checks if a component has red text color
     */
    public static boolean isRedText(Component component) {
        if (component == null) {
            return false;
        }

        TextColor color = component.color();
        return color != null && color.equals(NamedTextColor.RED);
    }

    /**
     * Extracts plain text from a component, removing all formatting
     */
    public static String getPlainText(Component component) {
        if (component == null) {
            return "";
        }
        return PlainTextComponentSerializer.plainText().serialize(component).trim();
    }

    /**
     * Checks if the text contains the C1 sign prefix
     */
    public static boolean containsSignPrefix(Component component) {
        String plainText = getPlainText(component);
        return plainText.contains(SIGN_PREFIX);
    }

    /**
     * Checks if a sign is valid (has blue text on line 1)
     */
    public static boolean isValidSign(Component line1) {
        return containsSignPrefix(line1) && isBlueText(line1);
    }

    /**
     * Checks if a sign is invalid (has red text on line 1)
     */
    public static boolean isInvalidSign(Component line1) {
        return containsSignPrefix(line1) && isRedText(line1);
    }
}
