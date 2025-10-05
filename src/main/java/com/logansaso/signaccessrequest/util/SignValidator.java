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

    public static final String GRANT_SIGN_PREFIX = "[c1-req]";
    public static final String REVOKE_SIGN_PREFIX = "[c1-drop]";

    public enum SignType {
        GRANT,
        REVOKE,
        UNKNOWN
    }

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
     * Checks if the text contains any C1 sign prefix
     */
    public static boolean containsSignPrefix(Component component) {
        String plainText = getPlainText(component);
        return plainText.contains(GRANT_SIGN_PREFIX) || plainText.contains(REVOKE_SIGN_PREFIX);
    }

    /**
     * Determines the sign type from the component
     */
    public static SignType getSignType(Component component) {
        String plainText = getPlainText(component).toLowerCase();
        if (plainText.contains(GRANT_SIGN_PREFIX)) {
            return SignType.GRANT;
        } else if (plainText.contains(REVOKE_SIGN_PREFIX)) {
            return SignType.REVOKE;
        }
        return SignType.UNKNOWN;
    }

    /**
     * Gets the appropriate prefix for a sign type
     */
    public static String getPrefixForType(SignType type) {
        switch (type) {
            case GRANT:
                return GRANT_SIGN_PREFIX;
            case REVOKE:
                return REVOKE_SIGN_PREFIX;
            default:
                return GRANT_SIGN_PREFIX;
        }
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
