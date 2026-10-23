package net.luckperms.api.model.user;
import java.util.UUID;
public interface UserManager {
    User getUser(UUID uuid);
    void saveUser(User user);
}
