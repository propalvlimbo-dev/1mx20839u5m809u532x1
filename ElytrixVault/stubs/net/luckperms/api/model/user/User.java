package net.luckperms.api.model.user;
import net.luckperms.api.node.NodeCollection;
public interface User {
    NodeCollection data();
    String getUsername();
}
