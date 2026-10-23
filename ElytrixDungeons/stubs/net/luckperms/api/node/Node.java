package net.luckperms.api.node;
public interface Node {
    String getKey();
    static Builder builder(String key) { return new Builder(key); }
    final class Builder {
        private final String key;
        private Builder(String key) { this.key = key; }
        public Builder value(boolean value) { return this; }
        public Node build() { return new SimpleNode(this.key); }
    }
    final class SimpleNode implements Node {
        private final String key;
        SimpleNode(String key) { this.key = key; }
        public String getKey() { return this.key; }
    }
}
