package com.niuqu.pickupcard.config;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 两份语言文件的钉子：<b>zh_cn 与 en_us 的 key 集必须一致</b>。
 *
 * <p>【为什么测它】2026-09-19 全量语言化后，界面文案全部走 key（约 150 条）。单语缺键的
 * 症状是"切到另一门语言就露出 key 原文"—— 在游戏里试出来要切语言、开界面、逐行看；
 * 这里两个集合一比，缺谁的名字当场报出来。措辞本身不测（那是人的事），测的是结构。
 */
class LangKeyConsistencyTest {

    private static Path langDir() {
        // gradle test 的工作目录是模块目录；从仓库根跑时回退一级
        for (Path p : List.of(Path.of("src/main/resources/assets/pickupcard/lang"),
                Path.of("../src/main/resources/assets/pickupcard/lang"))) {
            if (Files.isDirectory(p)) {
                return p;
            }
        }
        throw new AssertionError("找不到语言文件目录（工作目录 = " + Path.of(".") .toAbsolutePath() + "）");
    }

    private static Map<String, String> load(String file) throws Exception {
        return new Gson().fromJson(Files.readString(langDir().resolve(file)),
                new TypeToken<Map<String, String>>() { }.getType());
    }

    @Test
    void bothLanguagesHaveTheSameKeys() throws Exception {
        Map<String, String> zh = load("zh_cn.json");
        Map<String, String> en = load("en_us.json");
        assertEquals(zh.keySet(), en.keySet(),
                "两份语言文件的 key 集不一致（缺失的那边会在游戏里露出 key 原文）");
        assertFalse(zh.isEmpty());
    }

    @Test
    void valuesAreRealTranslationsNotBareKeys() throws Exception {
        Map<String, String> zh = load("zh_cn.json");
        Map<String, String> en = load("en_us.json");
        for (Map.Entry<String, String> e : zh.entrySet()) {
            assertFalse(e.getValue().isBlank(), e.getKey() + ": 中文文案是空白");
            assertFalse(e.getValue().contains("pickupcard."), e.getKey() + ": 中文文案没翻译（还是 key）");
            assertFalse(en.get(e.getKey()).isBlank(), e.getKey() + ": 英文文案是空白");
        }
        // 占位符必须两边都在：一边有 %s 另一边没有，格式化时会把另一边的参数吞掉或抛错
        for (Map.Entry<String, String> e : zh.entrySet()) {
            assertEquals(countSpecifiers(e.getValue()), countSpecifiers(en.get(e.getKey())),
                    e.getKey() + ": 两边的 % 占位符数量不一致");
        }
    }

    /** 粗数格式占位符：%s/%d/%.2f 这类。只看个数，不看类型 —— 类型错了游戏里当场炸，瞒不住。 */
    private static long countSpecifiers(String s) {
        return s.chars().filter(c -> c == '%').count();
    }
}
