package com.example.ragkb.note;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class AskController {

    private final RagService rag;

    public AskController(RagService rag) {
        this.rag = rag;
    }

    /**
     * 用 POST 而不是 GET：问题会进日志和浏览器历史，
     * 而且 GET 语义上是「只读查询」，这里会调模型、产生费用。
     */
    @PostMapping("/ask")
    public RagService.AskResponse ask(@RequestBody AskRequest req) {
        String question = req.q() == null ? "" : req.q().trim();
        if (question.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "q 不能为空");
        }
        int k = req.k() == null ? 3 : req.k();
        return rag.ask(question, k);
    }

    public record AskRequest(String q, Integer k) {}
}