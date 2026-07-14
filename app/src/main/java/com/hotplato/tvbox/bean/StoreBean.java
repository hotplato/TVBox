package com.hotplato.tvbox.bean;

/**
 * 多仓配置中的单个仓库项。
 */
public class StoreBean {
    private String name;
    private String url;

    public StoreBean() {
    }

    public StoreBean(String name, String url) {
        this.name = name;
        this.url = url;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}
