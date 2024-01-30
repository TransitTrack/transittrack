/* (C)2023 */
package org.transitclock.service.contract;

import java.rmi.Remote;
import java.rmi.RemoteException;
import org.transitclock.service.dto.IpcHoldingTime;

/**
 * Defines the RMI interface used for obtaining holding time information.
 *
 * @author Sean Og Crudden
 */
public interface HoldingTimeInterface extends Remote {

    IpcHoldingTime getHoldTime(String stopId, String vehicleId, String tripId) throws RemoteException;

    IpcHoldingTime getHoldTime(String stopId, String vehicleId) throws RemoteException;
}