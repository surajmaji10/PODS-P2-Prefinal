package me.akashmaj.demomarketplaceservice;

public class Color {
    public static void red(String format, Object... args) {
        String s =  String.format("\033[31;1m%s\033[0m", String.format(format, args));
        System.out.println(s);
    }

    public static void green(String format, Object... args) {
        String s =  String.format("\033[32;1m%s\033[0m", String.format(format, args));
        System.out.println(s);
    }

    public static void yellow(String format, Object... args) {
        String s = String.format("\033[33;1m%s\033[0m", String.format(format, args));
        System.out.println(s);
    }

    public static void blue(String format, Object... args) {
        String s =  String.format("\033[34;1m%s\033[0m", String.format(format, args));
        System.out.println(s);
    }

    public static void purple(String format, Object... args) {
        String s =  String.format("\033[35;1m%s\033[0m", String.format(format, args));
        System.out.println(s);
    }

    public static void cyan(String format, Object... args) {
        String s =  String.format("\033[36;1m%s\033[0m", String.format(format, args));
        System.out.println(s);
    }

    public static void white(String format, Object... args) {
        String s =  String.format("\033[37;1m%s\033[0m", String.format(format, args));
        System.out.println(s);
    }
} 

