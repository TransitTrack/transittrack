/* (C)2023 */
package org.transitclock.service.dto;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.Serializable;

import lombok.Getter;

import org.transitclock.domain.structs.AssignmentType;
import org.transitclock.domain.structs.AvlReport;
import org.transitclock.utils.Geo;
import org.transitclock.utils.Time;

/**
 * A serializable object used by RMI to transfer AVL data to client.
 *
 * @author SkiBu Smith
 */
@Getter
public class IpcAvl implements Serializable {

    private final String vehicleId;
    private final long time;
    private final float latitude;
    private final float longitude;
    private final float speed;
    private final float heading;
    private final String source;
    private final String assignmentId;
    private final AssignmentType assignmentType;
    private final String driverId;
    private final String licensePlate;
    private final int passengerCount;
    private final float passengerFullness;

    /**
     * @param vehicleId
     * @param time
     * @param latitude
     * @param longitude
     * @param speed
     * @param heading
     * @param source
     * @param assignmentId
     * @param assignmentType
     * @param driverId
     * @param licensePlate
     * @param passengerCount
     */
    public IpcAvl(
            String vehicleId,
            long time,
            float latitude,
            float longitude,
            float speed,
            float heading,
            String source,
            String assignmentId,
            AssignmentType assignmentType,
            String driverId,
            String licensePlate,
            int passengerCount) {
        this.vehicleId = vehicleId;
        this.time = time;
        this.latitude = latitude;
        this.longitude = longitude;
        this.speed = speed;
        this.heading = heading;
        this.source = source;
        this.assignmentId = assignmentId;
        this.assignmentType = assignmentType;
        this.driverId = driverId;
        this.licensePlate = licensePlate;
        this.passengerCount = passengerCount;
        this.passengerFullness = Float.NaN;
    }

    /**
     * @param avl
     */
    public IpcAvl(AvlReport avl) {
        this.vehicleId = avl.getVehicleId();
        this.time = avl.getTime();
        this.latitude = (float) avl.getLat();
        this.longitude = (float) avl.getLon();
        this.speed = avl.getSpeed();
        this.heading = avl.getHeading();
        this.source = avl.getSource();
        this.assignmentId = avl.getAssignmentId();
        this.assignmentType = avl.getAssignmentType();
        this.driverId = avl.getDriverId();
        this.licensePlate = avl.getLicensePlate();
        this.passengerCount = avl.getPassengerCount();
        this.passengerFullness = avl.getPassengerFullness();
    }

    /*
     * Needed as part of using a SerializationProxy. When Vehicle object
     * is serialized the SerializationProxy will instead be used.
     */
    private Object writeReplace() {
        return new SerializationProxy(this);
    }

    /*
     * Needed as part of using a SerializationProxy. Makes sure that Vehicle
     * object cannot be deserialized without using proxy, thereby eliminating
     * possibility of such an attack as described in "Effective Java".
     */
    private void readObject(ObjectInputStream stream) throws InvalidObjectException {
        throw new InvalidObjectException("Must use proxy instead");
    }

    @Override
    public String toString() {
        return "IpcAvl [vehicleId="
                + vehicleId
                + ", time="
                + Time.dateTimeStr(time)
                + ", latitude="
                + latitude
                + ", longitude="
                + longitude
                + ", speed="
                + Geo.speedFormat(speed)
                + ", heading="
                + Geo.headingFormat(heading)
                + ", source="
                + source
                + ", assignmentId="
                + assignmentId
                + ", assignmentType="
                + assignmentType
                + ", driverId="
                + driverId
                + ", licensePlate="
                + licensePlate
                + ", passengerCount="
                + passengerCount
                + ", passengerFullness="
                + passengerFullness
                + "]";
    }

    /*
     * SerializationProxy is used so that this class can be immutable
     * and so that can do versioning of objects.
     */
    private static class SerializationProxy implements Serializable {
        private static final long serialVersionUID = 6220698347690060245L;
        private static final short serializationVersion = 0;
        // Exact copy of fields of Vehicle object
        private String vehicleId;
        private long time;
        private float latitude;
        private float longitude;
        private float speed; // in m/s
        private float heading;
        private String source;
        private String assignmentId;
        private AssignmentType assignmentType;
        private String driverId;
        private String licensePlate;
        private int passengerCount;

        /*
         * Only to be used within this class.
         */
        private SerializationProxy(IpcAvl avl) {
            this.vehicleId = avl.vehicleId;
            this.time = avl.time;
            this.latitude = avl.latitude;
            this.longitude = avl.longitude;
            this.speed = avl.speed;
            this.heading = avl.heading;
            this.source = avl.source;
            this.assignmentId = avl.assignmentId;
            this.assignmentType = avl.assignmentType;
            this.driverId = avl.driverId;
            this.licensePlate = avl.licensePlate;
            this.passengerCount = avl.passengerCount;
        }

        /*
         * When object is serialized writeReplace() causes this
         * SerializationProxy object to be written. Write it in a
         * custom way that includes a version ID so that clients
         * and servers can have two different versions of code.
         */
        private void writeObject(java.io.ObjectOutputStream stream) throws IOException {
            stream.writeShort(serializationVersion);
            stream.writeObject(vehicleId);
            stream.writeLong(time);
            stream.writeFloat(latitude);
            stream.writeFloat(longitude);
            stream.writeFloat(speed);
            stream.writeFloat(heading);
            stream.writeObject(source);
            stream.writeObject(assignmentId);
            stream.writeObject(assignmentType);
            stream.writeObject(driverId);
            stream.writeObject(licensePlate);
            stream.writeInt(passengerCount);
        }

        /*
         * When an object is read in it will be a SerializatProxy object
         * due to writeReplace() being used by the enclosing class. When
         * such an object is deserialized this method will be called and
         * the SerializationProxy object is converted to an enclosing
         * class object.
         */
        private Object readResolve() {
            return new IpcAvl(
                    vehicleId,
                    time,
                    latitude,
                    longitude,
                    speed,
                    heading,
                    source,
                    assignmentId,
                    assignmentType,
                    driverId,
                    licensePlate,
                    passengerCount);
        }

        /*
         * Custom method of deserializing a SerializationProy object.
         */
        private void readObject(ObjectInputStream stream) throws IOException, ClassNotFoundException {
            short readVersion = stream.readShort();
            if (serializationVersion != readVersion) {
                throw new IOException("Serialization error when reading "
                                              + getClass().getSimpleName()
                                              + " object. Read serializationVersion="
                                              + readVersion);
            }

            // serialization version is OK so read in object
            vehicleId = (String) stream.readObject();
            time = stream.readLong();
            latitude = stream.readFloat();
            longitude = stream.readFloat();
            speed = stream.readFloat();
            heading = stream.readFloat();
            source = (String) stream.readObject();
            assignmentId = (String) stream.readObject();
            assignmentType = (AssignmentType) stream.readObject();
            driverId = (String) stream.readObject();
            licensePlate = (String) stream.readObject();
            passengerCount = stream.readInt();
        }
    }
}
