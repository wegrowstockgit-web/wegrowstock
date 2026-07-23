package com.invsys.documents;

import com.invsys.core.common.ApiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.xhtmlrenderer.pdf.ITextRenderer;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

/**
 * Thymeleaf HTML → Flying Saucer (OpenPDF) PDF pipeline.
 * Keeps rendering concerns out of financial domain services.
 */
@Service
public class DocumentRenderingService {

    private static final Logger log = LoggerFactory.getLogger(DocumentRenderingService.class);

    private final TemplateEngine templateEngine;

    public DocumentRenderingService(TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    /**
     * @param templateName classpath template under {@code templates/} without {@code .html}
     *                     (e.g. {@code documents/invoice_template})
     */
    public byte[] generatePdf(String templateName, Map<String, Object> variables) {
        if (templateName == null || templateName.isBlank()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_TEMPLATE", "Template name required");
        }
        String html = renderHtml(templateName, variables == null ? Map.of() : variables);
        return htmlToPdf(html);
    }

    public String renderHtml(String templateName, Map<String, Object> variables) {
        Context context = new Context(Locale.US);
        context.setVariables(variables);
        return templateEngine.process(templateName, context);
    }

    byte[] htmlToPdf(String html) {
        String xhtml = ensureXhtml(html);
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            ITextRenderer renderer = new ITextRenderer();
            renderer.setDocumentFromString(xhtml);
            renderer.layout();
            renderer.createPDF(out);
            byte[] pdf = out.toByteArray();
            if (pdf.length < 5 || pdf[0] != '%' || pdf[1] != 'P' || pdf[2] != 'D' || pdf[3] != 'F') {
                throw new IllegalStateException("Renderer did not produce a PDF header");
            }
            log.debug("Generated PDF bytes={}", pdf.length);
            return pdf;
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("PDF render failed: {}", ex.toString());
            throw new ApiException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "PDF_RENDER_FAILED",
                    "Could not render document PDF");
        }
    }

    /**
     * Flying Saucer expects well-formed XHTML. Thymeleaf usually emits HTML5;
     * normalize the common voids and declare XHTML when missing.
     */
    static String ensureXhtml(String html) {
        if (html == null || html.isBlank()) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "EMPTY_TEMPLATE", "Template rendered empty");
        }
        String out = html;
        if (!out.contains("http://www.w3.org/1999/xhtml")) {
            out = out.replaceFirst(
                    "<html(\\s|>)",
                    "<html xmlns=\"http://www.w3.org/1999/xhtml\"$1");
        }
        // Self-close common void tags for XML parser
        out = out.replaceAll("(?i)<meta([^>]*?)(?<!/)>", "<meta$1/>");
        out = out.replaceAll("(?i)<br(?![^>]*/>)\\s*>", "<br/>");
        out = out.replaceAll("(?i)<hr(?![^>]*/>)\\s*>", "<hr/>");
        out = out.replaceAll("(?i)<img([^>]*?)(?<!/)>", "<img$1/>");
        return new String(out.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
    }
}
