package com.codereview.plugin.model;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;

public class ValidateSpecRequest {
    private String serviceName;
    private Params params;

    public ValidateSpecRequest(String text, String filePath) {
        this.serviceName = "api.validate.spec.entry";
        this.params = new Params(text, filePath);
    }

    public String getServiceName() {
        return serviceName;
    }

    public Params getParams() {
        return params;
    }

    public static class Params {
        private Context context;
        private List<Data> data;

        public Params(String text, String filePath) {
            this.context = new Context();
            this.data = new ArrayList<>();
            this.data.add(new Data(text, filePath));
        }

        public Context getContext() {
            return context;
        }

        public List<Data> getData() {
            return data;
        }
    }

    public static class Context {
        private String agentCode = "GetVerificationSpec";
        private String agentVersion = "1";
        private String applicationCode = "CodeValidatorGen";
        private String tenantId = "digiwinBmOpt";

        public String getAgentCode() {
            return agentCode;
        }

        public String getAgentVersion() {
            return agentVersion;
        }

        public String getApplicationCode() {
            return applicationCode;
        }

        public String getTenantId() {
            return tenantId;
        }
    }

    public static class Data {
        private String text;
        private String filePath;

        public Data(String text, String filePath) {
            this.text = text;
            this.filePath = filePath;
        }

        public String getText() {
            return text;
        }

        public String getFilePath() {
            return filePath;
        }
    }
} 