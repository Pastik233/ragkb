package com.example.ragkb.document;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import org.xml.sax.SAXException;

@Component
public class DocumentParser {

    private final int maxExtractedChars;

    /** 解析器内部只有注册表，没有可变的解析状态，可以全局共用一个实例 */
    private final AutoDetectParser parser = new AutoDetectParser();

    public DocumentParser(
            @Value("${app.ingestion.max-extracted-chars:2000000}")
            int maxExtractedChars) {
        this.maxExtractedChars = maxExtractedChars;
    }

    public ParsedDocument parse(MultipartFile file)
            throws IOException, TikaException, SAXException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }

        Metadata metadata = new Metadata();
        String filename = Objects.requireNonNullElse(
                file.getOriginalFilename(), "upload");
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, filename);

        try (InputStream input = file.getInputStream()) {
            BodyContentHandler handler =
                    new BodyContentHandler(maxExtractedChars);

            // ParseContext 与 Handler 每次新建，解析器实例复用
            parser.parse(input, handler, metadata, new ParseContext());

            String text = normalize(handler.toString());
            if (text.isEmpty()) {
                throw new IllegalArgumentException(
                        "没有提取到文本，扫描件可能需要 OCR");
            }

            return new ParsedDocument(
                    text, metadata.get(Metadata.CONTENT_TYPE));
        }
    }

    private String normalize(String raw) {
        return raw.replace("\r\n", "\n")
                .replace('\r', '\n')
                .replaceAll("[\\t ]+\\n", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    public record ParsedDocument(String text, String mimeType) {}
}
