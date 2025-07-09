package com.codereview.plugin.model;

import java.util.List;

/**
 * 代码审查反馈请求模型
 */
public class ReviewFeedbackRequest {
    private List<FeedbackData> data;

    public ReviewFeedbackRequest(List<FeedbackData> data) {
        this.data = data;
    }

    public List<FeedbackData> getData() {
        return data;
    }

    public void setData(List<FeedbackData> data) {
        this.data = data;
    }

    /**
     * 反馈数据项
     */
    public static class FeedbackData {
        private String code_submt_recd_no;  // 代码提交记录号
        private String code_file_no;        // 代码文件号
        private String code_slice_no;       // 代码片段号
        private int seq;                    // 序号
        private String user_feedb_stat;     // 用户反馈状态：2-已确认，3-误报
        private String user_feedb_desc;     // 用户反馈描述

        public FeedbackData(String code_submt_recd_no, String code_file_no, String code_slice_no, 
                           int seq, String user_feedb_stat, String user_feedb_desc) {
            this.code_submt_recd_no = code_submt_recd_no;
            this.code_file_no = code_file_no;
            this.code_slice_no = code_slice_no;
            this.seq = seq;
            this.user_feedb_stat = user_feedb_stat;
            this.user_feedb_desc = user_feedb_desc;
        }

        // Getters and Setters
        public String getCode_submt_recd_no() {
            return code_submt_recd_no;
        }

        public void setCode_submt_recd_no(String code_submt_recd_no) {
            this.code_submt_recd_no = code_submt_recd_no;
        }

        public String getCode_file_no() {
            return code_file_no;
        }

        public void setCode_file_no(String code_file_no) {
            this.code_file_no = code_file_no;
        }

        public String getCode_slice_no() {
            return code_slice_no;
        }

        public void setCode_slice_no(String code_slice_no) {
            this.code_slice_no = code_slice_no;
        }

        public int getSeq() {
            return seq;
        }

        public void setSeq(int seq) {
            this.seq = seq;
        }

        public String getUser_feedb_stat() {
            return user_feedb_stat;
        }

        public void setUser_feedb_stat(String user_feedb_stat) {
            this.user_feedb_stat = user_feedb_stat;
        }

        public String getUser_feedb_desc() {
            return user_feedb_desc;
        }

        public void setUser_feedb_desc(String user_feedb_desc) {
            this.user_feedb_desc = user_feedb_desc;
        }
    }
} 