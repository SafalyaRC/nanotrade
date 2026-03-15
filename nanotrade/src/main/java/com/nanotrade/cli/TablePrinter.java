package com.nanotrade.cli;

public class TablePrinter {
    private final int width; // inner width between the ║ borders

    public TablePrinter(int width) {
        this.width = width;
    }

    public void top() {
        System.out.println("  ╔" + repeat("═", width) + "╗");
    }

    public void mid() {
        System.out.println("  ╠" + repeat("═", width) + "╣");
    }

    public void bot() {
        System.out.println("  ╚" + repeat("═", width) + "╝");
    }

    public void row(String text) {
        System.out.println("  ║ " + pad(text, width - 2) + " ║");
    }

    public void rowCentered(String text) {
        int totalPad = width - 2 - text.length();
        int left = totalPad / 2;
        int right = totalPad - left;
        System.out.println("  ║ " + repeat(" ", left) + text + repeat(" ", right) + " ║");
    }

    public void blank() {
        row("");
    }

    // Pads or truncates s to exactly len chars
    public static String pad(String s, int len) {
        if (s == null)
            s = "";
        if (s.length() >= len)
            return s.substring(0, len);
        return s + repeat(" ", len - s.length());
    }

    public static String repeat(String c, int n) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++)
            sb.append(c);
        return sb.toString();
    }
}