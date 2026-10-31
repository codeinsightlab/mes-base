package dev.mesbase.business.basic.domain;

/** Material query criteria with a code-or-name prefix search for remote selectors. */
public final class MaterialSearchExample extends MaterialExample {
    private String keywordPrefix;

    public String getKeywordPrefix() {
        return keywordPrefix;
    }

    public void setKeywordPrefix(String keywordPrefix) {
        this.keywordPrefix = keywordPrefix;
    }
}
