package org.grassroot.android.services.model;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

import org.grassroot.android.utils.Constant;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

/**
 * Created by paballo on 2016/05/04.
 */
public class Group implements Parcelable, Comparable<Group> {

    private String groupUid;
    private String groupName;
    private String groupCreator;
    private String role;
    private Integer groupMemberCount;

    private String joinCode;
    private String lastChangeType;
    private String description;

    private Date date;
    private DateTime dateTime;
    private String dateTimeStringISO;

    private List<String> permissions = new ArrayList<>(); // todo: convert this to a set so can do fast hashing

    public String getGroupUid() {
        return groupUid;
    }

    public void setGroupUid(String groupUid) {
        this.groupUid = groupUid;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getGroupCreator() {
        return groupCreator;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Integer getGroupMemberCount() {
        return groupMemberCount;
    }

    public DateTime getDateTime() {
        return dateTime;
    }

    public void setDateTime(DateTime dateTime) {
        this.dateTime = dateTime;
    }

    public String getJoinCode() { return joinCode; }

    public String getDateTimeStringISO() {
        if (dateTimeStringISO == null || dateTimeStringISO.equals("")) {
            dateTimeStringISO = Constant.isoDateTimeSDF.format(getDate());
            return dateTimeStringISO;
        } else {
            return dateTimeStringISO;
        }
    }

    public String getLastChangeType() { return lastChangeType; }

    public Date getDate() {
        if (date == null) {
            if (dateTime != null) {
                constructDate();
                return date;
            } else if (dateTimeStringISO != null && !dateTimeStringISO.equals("")) {
                try {
                    date = Constant.isoDateTimeSDF.parse(dateTimeStringISO);
                    return date;
                } catch (ParseException e) {
                    e.printStackTrace();
                    return null;
                }
            } else {
                throw new UnsupportedOperationException("Error! Group needs at least one source of a datetime");
            }
        } else {
            return date;
        }
    }

    public List<String> getPermissions() {
        return permissions;
    }

    public void setPermissions(List<String> permissions) {
        this.permissions = permissions;
    }

    private void constructDate() {
        Calendar calendar = Calendar.getInstance();

        // NB: Java 7 datetime requires these to be set in order (argh, for Joda/Java8)
        calendar.set(Calendar.YEAR, dateTime.getYear());
        calendar.set(Calendar.MONTH, dateTime.getMonthValue() - 1);
        calendar.set(Calendar.DAY_OF_MONTH, dateTime.getDayOfMonth());

        calendar.set(Calendar.HOUR_OF_DAY, dateTime.getHour());
        calendar.set(Calendar.MINUTE, dateTime.getMinute());
        calendar.set(Calendar.SECOND, dateTime.getSecond());
        this.date = calendar.getTime();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.groupUid);
        dest.writeString(this.groupName);
        dest.writeString(this.description);
        dest.writeString(this.groupCreator);
        dest.writeString(this.role);
        dest.writeInt(this.groupMemberCount);
        dest.writeString(getDateTimeStringISO());
        dest.writeString(this.lastChangeType);
        dest.writeString(this.joinCode);
        dest.writeStringList(this.permissions);
    }

    protected Group(Parcel in) {
        groupUid = in.readString();
        groupName = in.readString();
        description = in.readString();
        groupCreator = in.readString();
        role = in.readString();
        groupMemberCount = in.readInt();
        dateTimeStringISO = in.readString();
        lastChangeType = in.readString();
        joinCode = in.readString();
        permissions = in.createStringArrayList();
    }

    public static final Creator<Group> CREATOR = new Creator<Group>() {
        @Override
        public Group createFromParcel(Parcel in) {
            return new Group(in);
        }

        @Override
        public Group[] newArray(int size) {
            return new Group[size];
        }
    };

    @Override
    public int compareTo(Group g2) {
        return this.getDate().compareTo(g2.getDate());
    }

    public static Comparator<Group> GroupRoleComparator = new Comparator<Group>() {
        @Override
        public int compare(Group group, Group t1) {
            int compareRoles = compareRoleNames(group.getRole(), t1.getRole());
            if (compareRoles != 0)
                return compareRoles;
            else
                return group.compareTo(t1);
        }

        private int compareRoleNames(String roleFirst, String roleSecond) {
            if (roleFirst.equals(roleSecond)) {
                return 0;
            } else if (roleFirst.equals(Constant.ROLE_ORDINARY_MEMBER)) {
                return -1;
            } else if (roleFirst.equals(Constant.ROLE_COMMITTEE_MEMBER) && roleSecond.equals(Constant.ROLE_GROUP_ORGANIZER)) {
                return -1;
            } else {
                return 1;
            }
        }
    };

    @Override
    public String toString() {
        return "Group{" +
                "groupUid='" + groupUid + '\'' +
                ", lastChangeType='" + lastChangeType + '\'' +
                ", groupName='" + groupName + '\'' +
                '}';
    }
}
