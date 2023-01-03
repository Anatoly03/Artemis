package de.tum.in.www1.artemis.web.rest.metis.conversation.dtos;

import de.tum.in.www1.artemis.domain.metis.conversation.Channel;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class ChannelDTO extends ConversationDTO {

    // Note: the constrains should be consistent with the ones defined in Channel.java
    @NotNull
    @Size(min = 1, max = 20)
    private String name;

    @Size(min = 1, max = 250)
    private String description;

    @Size(min = 1, max = 250)
    private String topic;

    private Boolean isPublic;

    private Boolean isAnnouncementChannel;

    private Boolean isArchived;

    // property not taken from entity
    /**
     * A course instructor has channel moderation rights but is not necessarily a moderator of the channel
     */
    private Boolean hasChannelModerationRights;

    // property not taken from entity
    /**
     * Member of the channel that is also a moderator of the channel
     */
    private Boolean isChannelModerator;

    public ChannelDTO(Channel channel) {
        super(channel, "channel");
        this.name = channel.getName();
        this.description = channel.getDescription();
        this.isPublic = channel.getIsPublic();
        this.topic = channel.getTopic();
        this.isArchived = channel.getIsArchived();
        this.isAnnouncementChannel = channel.getIsAnnouncementChannel();
    }

    public ChannelDTO() {
        this.setType("channel");
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public Boolean getIsPublic() {
        return isPublic;
    }

    public void setIsPublic(Boolean isPublic) {
        this.isPublic = isPublic;
    }

    public Boolean getIsAnnouncementChannel() {
        return isAnnouncementChannel;
    }

    public void setIsAnnouncementChannel(Boolean announcementChannel) {
        isAnnouncementChannel = announcementChannel;
    }

    public Boolean getIsArchived() {
        return isArchived;
    }

    public void setIsArchived(Boolean archived) {
        isArchived = archived;
    }

    public Boolean getHasChannelModerationRights() {
        return hasChannelModerationRights;
    }

    public void setHasChannelModerationRights(Boolean hasChannelModerationRights) {
        this.hasChannelModerationRights = hasChannelModerationRights;
    }

    public Boolean getIsChannelModerator() {
        return isChannelModerator;
    }

    public void setIsChannelModerator(Boolean isChannelModerator) {
        this.isChannelModerator = isChannelModerator;
    }

    @Override
    public String toString() {
        return "ChannelDTO{" + "name='" + name + '\'' + ", description='" + description + '\'' + ", topic='" + topic + '\'' + ", isPublic=" + isPublic + ", isAnnouncementChannel="
                + isAnnouncementChannel + ", isArchived=" + isArchived + ", isChannelModerator=" + isChannelModerator + ", hasChannelModerationRights=" + hasChannelModerationRights
                + "}" + super.toString();
    }
}
