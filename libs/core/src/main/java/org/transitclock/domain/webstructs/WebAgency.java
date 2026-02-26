/* (C)2023 */
package org.transitclock.domain.webstructs;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.util.List;

import org.transitclock.domain.repository.AgencyRepository;
import org.transitclock.domain.structs.ActiveRevision;
import org.transitclock.domain.structs.Agency;
import org.transitclock.utils.Encryption;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.annotations.DynamicUpdate;

/**
 * For keeping track of agency data for a website. Contains info such as the host of the agency so
 * that can use RMI to connect to it.
 *
 * @author SkiBu Smith
 */
@Entity
@DynamicUpdate
@Data
@Slf4j
@Table(name = "web_agencies")
public class WebAgency {

    @Id
    @Column(length = 60)
    private final String agencyId;
    @Column(length = 120)
    private final String hostName;
    @Column
    private final boolean active;
    @Column(length = 60)
    private final String dbName;
    @Column(length = 60)
    private final String dbType;
    @Column(length = 120)
    private final String dbHost;
    @Column(length = 60)
    private final String dbUserName;
    // Passwords should be stored encrypted since multiple people might have
    // access to the database containing the WebAgency objects.
    @Column(length = 60)
    private final String dbEncryptedPassword;
    // For getting GTFS data about agency
    @Transient
    private Agency agency;
    @Transient
    private String agencyName;

    /**
     * Simple constructor.
     *
     * @param agencyId
     * @param hostName
     * @param active
     * @param dbName
     * @param dbType
     * @param dbHost
     * @param dbUserName
     * @param dbPassword The non-encrypted password
     */
    public WebAgency(
            String agencyId,
            String hostName,
            boolean active,
            String dbName,
            String dbType,
            String dbHost,
            String dbUserName,
            String dbPassword) {
        this.agencyId = agencyId;
        this.hostName = hostName;
        this.active = active;
        this.dbName = dbName;
        this.dbType = dbType;
        this.dbHost = dbHost;
        this.dbUserName = dbUserName;
        this.dbEncryptedPassword = Encryption.encrypt(dbPassword);
    }

    /**
     * Needed because Hibernate requires no-arg constructor for reading in data
     */
    @SuppressWarnings("unused")
    protected WebAgency() {
        this.agencyId = null;
        this.hostName = null;
        this.active = false;
        this.dbName = null;
        this.dbType = null;
        this.dbHost = null;
        this.dbUserName = null;
        this.dbEncryptedPassword = null;
    }

    /**
     * Returns the first (there can be multiple) GTFS agency object for the specified agencyId. The
     * GTFS agency object is cached. First time it is accessed it is read from server via RMI. If
     * can't connect via RMI then will timeout after a few seconds. This means that any call to this
     * method can take several seconds if there is a problem.
     *
     * @return The Agency object, or null if can't access the agency via RMI
     */
    public Agency getAgency() {
        // If agency hasn't been accessed yet do so now...
        //TODO: need to find a way to add this back
        if (agency == null) {
            int configRev = ActiveRevision.get(agencyId).getConfigRev();
            List<Agency> agencies = AgencyRepository.getAgencies(agencyId, configRev);
            agency = agencies.getFirst();
        }

        // Return the RMI based agency object
        return agency;
    }

    /**
     * Returns the GTFS agency name. It is obtained via RMI the first time this method is access for
     * an agency. Subsequent times a cached value is returned. If cannot connect to RMI then the
     * agencyId is returned.
     *
     * @return The GTFS agency name
     */
    public String getAgencyName() {
        if (agencyName != null) {
            return agencyName;
        }

        // Get agency object via RMI
        Agency agency = getAgency();

        // Get the agency name. If couldn't connect via RMI then use the
        // agency ID. Cache the results so don't try to keep accessing via
        // RMI, which is especially important if agency isn't running which
        // means that would hang for a few seconds each call until timed out.
        agencyName = agency != null ? agency.getName() : agencyId;
        return agencyName;
    }
}
