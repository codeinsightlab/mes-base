package dev.mesbase.system.domain.dto;
import java.util.List;
public class UserNamesQuery {
    private List<String> userNames;
    public UserNamesQuery() { }
    public UserNamesQuery(List<String> userNames) { this.userNames=userNames; }
    public List<String> getUserNames() { return userNames; }
    public void setUserNames(List<String> userNames) { this.userNames=userNames; }
}
