package com.example.translator;

import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);

        try {
            System.out.print("Enter text to translate: ");
            String text = sc.nextLine();

            System.out.print("Translate to (Hindi/Spanish/French/German/Telugu): ");
            String lang = sc.nextLine();

            String translated = Translator.translate(text, lang);

            System.out.println("\nTranslated Text: " + translated);

        } catch (Exception e) {
            System.out.println("Error occurred: " + e.getMessage());
        } finally {
            sc.close();
        }
    }
}