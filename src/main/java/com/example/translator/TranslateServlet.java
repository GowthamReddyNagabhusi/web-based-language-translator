package com.example.translator;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;

public class TranslateServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {

        String text = req.getParameter("text");
        String lang = req.getParameter("lang");
        System.out.println("TEXT=" + text + " LANG=" + lang);
        resp.setContentType("text/plain");

        try {
            String translated = Translator.translate(text, lang);
            resp.getWriter().write(translated);
        } catch (Exception e) {
            resp.getWriter().write("Error: " + e.getMessage());
        }
    }
}
