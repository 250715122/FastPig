package com.gt.kb.search;

import com.gt.AppConfig;
import com.gt.config.ConfigKeys;

/**
 * 人与 AI 两套检索档位。
 *
 * 人用的是联想面板：要多样性（每篇配额）、要短摘要；AI 用的是知识输入：
 * 要完整块正文和来源字段，不限每篇配额，且只取明确开放给 AI 的内容。
 * 参数全部走 ConfigKeys，设置页可改。
 */
public enum SearchProfile {

    HUMAN(false),
    AI(true);

    private final boolean fullBody;

    SearchProfile(boolean fullBody) {
        this.fullBody = fullBody;
    }

    public int topK() {
        return AppConfig.getInstance().getInt(this == AI ? ConfigKeys.SEARCH_AI_TOPK : ConfigKeys.SEARCH_HUMAN_TOPK);
    }

    /** 每个通道各取多少候选。融合前截断，不是融合后。 */
    public int candidates() {
        return AppConfig.getInstance().getInt(
                this == AI ? ConfigKeys.SEARCH_AI_CANDIDATES : ConfigKeys.SEARCH_HUMAN_CANDIDATES);
    }

    /** 每篇笔记最多露出几个知识块；0 表示不限。 */
    public int perNoteQuota() {
        int quota = AppConfig.getInstance().getInt(
                this == AI ? ConfigKeys.SEARCH_AI_PER_NOTE : ConfigKeys.SEARCH_HUMAN_PER_NOTE);
        return quota <= 0 ? Integer.MAX_VALUE : quota;
    }

    /** AI 档位返回完整知识块正文，人档位只返回摘要。 */
    public boolean fullBody() {
        return fullBody;
    }
}