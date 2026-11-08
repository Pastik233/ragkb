package com.example.ragkb.note;

/**
 * POST /notes 的请求体。
 * 只声明客户端能传的字段：id 和 createdAt 由服务端生成，不允许客户端指定。
 * 用 record 是因为它天生就有「全参构造 + 按名字读取组件」的能力，
 * Jackson 能直接拿 JSON 里的 title / content 填进来，不用写 getter/setter。
 */
public record NoteRequest(String title, String content) {
}
