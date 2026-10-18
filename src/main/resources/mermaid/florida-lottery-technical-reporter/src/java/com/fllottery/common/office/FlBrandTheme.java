package com.fllottery.common.office;

import java.awt.Color;

/**
 * Enterprise Design System & Brand Palette Tokens for Florida Lottery Office & Vector Documents.
 *
 * <p>Single source of truth for:
 * <ul>
 *   <li>Official Corporate Hex Palette (Plum Primary Theme, Deep Navy, Accent Gold, Forest Green)</li>
 *   <li>Java AWT {@link Color} equivalents for high-DPI Retina Java2D vector graphics</li>
 *   <li>Typography standards (Segoe UI for documentation, Consolas for code listings)</li>
 *   <li>Standard Apache POI margin and layout twip measurements</li>
 * </ul>
 *
 * @author Ryan Stokes (Programmer Analyst III)
 * @author Florida Lottery Architecture Group
 * @version 1.0.0
 */
public final class FlBrandTheme {

    private FlBrandTheme() {
        // Token constants
    }

    // Official Florida Lottery Corporate Hex Colors
    public static final String HEX_PLUM = "5C1D48";         // #5C1D48 Royal Plum (Primary brand)
    public static final String HEX_PLUM_DARK = "3B102E";    // #3B102E Deep Dark Plum (High contrast header)
    public static final String HEX_PLUM_MED = "7A2A60";     // #7A2A60 Medium Burgundy Plum (Secondary)
    public static final String HEX_PLUM_LIGHT = "F5EEF8";   // #F5EEF8 Soft Plum Tint (Callout backgrounds)
    public static final String HEX_PLUM_BORDER = "C4B5D0";  // #C4B5D0 Soft Plum Border Divider

    public static final String HEX_NAVY = "002B49";         // #002B49 Deep Navy
    public static final String HEX_BLUE = "005A9C";         // #005A9C Florida Lottery Blue
    public static final String HEX_GOLD = "DAA520";         // #DAA520 Accent Gold (PK & Key highlight)
    public static final String HEX_GREEN = "16A34A";        // #16A34A Forest Green (Success / Verified)
    public static final String HEX_RED = "B91C1C";          // #B91C1C Rejection / Error Red
    public static final String HEX_SLATE = "475569";        // #475569 Borders & Relational Connectors
    public static final String HEX_LIGHT_BG = "F8FAFC";     // #F8FAFC Clean Canvas / Shape Background
    public static final String HEX_BORDER = "CBD5E1";       // #CBD5E1 Table Border Gray

    // Java AWT Color Equivalents for High-DPI Java2D Vector Rendering
    public static final Color AWT_PLUM = new Color(0x5C, 0x1D, 0x48);
    public static final Color AWT_PLUM_DARK = new Color(0x3B, 0x10, 0x2E);
    public static final Color AWT_NAVY = new Color(0x00, 0x2B, 0x49);
    public static final Color AWT_BLUE = new Color(0x00, 0x5A, 0x9C);
    public static final Color AWT_GOLD = new Color(0xDA, 0xA5, 0x20);
    public static final Color AWT_GREEN = new Color(0x16, 0xA3, 0x4A);
    public static final Color AWT_RED = new Color(0xB9, 0x1C, 0x1C);
    public static final Color AWT_SLATE = new Color(0x47, 0x55, 0x69);
    public static final Color AWT_LIGHT_BG = new Color(0xF8, 0xFA, 0xFC);
    public static final Color AWT_BORDER = new Color(0xCB, 0xD5, 0xE1);

    // Typography
    public static final String FONT_TITLE = "Segoe UI";
    public static final String FONT_BODY = "Segoe UI";
    public static final String FONT_CODE = "Consolas";

    // Standard Apache POI Measurements (1 inch = 1440 twips)
    public static final int MARGIN_TWIPS_0_75_INCH = 1080;   // 0.75 inch standard margins
    public static final int MARGIN_TWIPS_0_5_INCH  = 720;    // 0.50 inch compact margins
}
