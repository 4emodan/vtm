/*
 * Copyright 2010, 2011, 2012 mapsforge.org
 * Copyright 2012 Hannes Janetzek
 *
 * This file is part of the OpenScienceMap project (http://www.opensciencemap.org).
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.oscim.core;

import org.oscim.utils.FastMath;

/**
 * A GeoPoint represents an immutable pair of latitude and longitude
 * coordinates.
 */
public class GeoPoint implements Comparable<GeoPoint> {
	/**
	 * Conversion factor from degrees to microdegrees.
	 */
	private static final double CONVERSION_FACTOR = 1000000d;

	/**
	 * The latitude value of this GeoPoint in microdegrees (degrees * 10^6).
	 */
	private final double latitude;

	/**
	 * The longitude value of this GeoPoint in microdegrees (degrees * 10^6).
	 */
	private final double longitude;

	/**
	 * The hash code of this object.
	 */
	private int hashCodeValue = 0;

	/**
	 * @param lat
	 *            the latitude in degrees, will be limited to the possible
	 *            latitude range.
	 * @param lon
	 *            the longitude in degrees, will be limited to the possible
	 *            longitude range.
	 */
	public GeoPoint(double lat, double lon) {
		lat = FastMath.clamp(lat,
		                     MercatorProjection.LATITUDE_MIN,
		                     MercatorProjection.LATITUDE_MAX);
		this.latitude = lat;
		lon = FastMath.clamp(lon,
		                     MercatorProjection.LONGITUDE_MIN,
		                     MercatorProjection.LONGITUDE_MAX);
		this.longitude = lon;
	}

	/**
	 * @param latitudeE6
	 *            the latitude in microdegrees (degrees * 10^6), will be limited
	 *            to the possible latitude range.
	 * @param longitudeE6
	 *            the longitude in microdegrees (degrees * 10^6), will be
	 *            limited to the possible longitude range.
	 */
	public GeoPoint(int latitudeE6, int longitudeE6) {
		this(latitudeE6 / CONVERSION_FACTOR, longitudeE6 / CONVERSION_FACTOR);
	}

	public void project(Point out) {
		out.x = MercatorProjection.longitudeToX(longitude);
		out.y = MercatorProjection.latitudeToY(latitude);
	}

	@Override
	public int compareTo(GeoPoint geoPoint) {
		if (this.getLongitudeE6() > geoPoint.getLongitudeE6()) {
			return 1;
		} else if (this.getLongitudeE6() < geoPoint.getLongitudeE6()) {
			return -1;
		} else if (this.getLatitudeE6() > geoPoint.getLatitudeE6()) {
			return 1;
		} else if (this.getLatitudeE6() < geoPoint.getLatitudeE6()) {
			return -1;
		}
		return 0;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		} else if (!(obj instanceof GeoPoint)) {
			return false;
		}
		GeoPoint other = (GeoPoint) obj;
		if (this.getLatitudeE6() != other.getLatitudeE6()) {
			return false;
		} else if (this.getLongitudeE6() != other.getLongitudeE6()) {
			return false;
		}
		return true;
	}

	/**
	 * @return the latitude value of this GeoPoint in degrees.
	 */
	public double getLatitude() {
		return latitude;
	}

	/**
	 * @return the longitude value of this GeoPoint in degrees.
	 */
	public double getLongitude() {
		return longitude;
	}

	public int getLatitudeE6() {
		return (int) (latitude * CONVERSION_FACTOR);
	}

	public int getLongitudeE6() {
		return (int) (longitude * CONVERSION_FACTOR);
	}

	@Override
	public int hashCode() {
		if (this.hashCodeValue == 0)
			this.hashCodeValue = calculateHashCode();

		return this.hashCodeValue;
	}

	@Override
	public String toString() {
		return new StringBuilder()
		    .append("[lat=")
		    .append(this.getLatitude())
		    .append(",lon=")
		    .append(this.getLongitude())
		    .append("]")
		    .toString();
	}

	/**
	 * @return the hash code of this object.
	 */
	private int calculateHashCode() {
		int result = 7;
		result = 31 * result + this.getLatitudeE6();
		result = 31 * result + this.getLongitudeE6();
		return result;
	}

	// ===========================================================
	// Methods from osmdroid
	// Copyright 2012 osmdroid authors: Nicolas Gramlich,
	// Theodore Hong
	// ===========================================================

	public static final double DEG2RAD = (Math.PI / 180.0);
	public static final double RAD2DEG = (180.0 / Math.PI);
	// http://en.wikipedia.org/wiki/Earth_radius#Equatorial_radius
	public static final int RADIUS_EARTH_METERS = 6378137;

	/**
	 * @see "http://www.geocities.com/DrChengalva/GPSDistance.html"
	 * @param other
	 *            ...
	 * @return distance in meters
	 */
	public double distanceTo(GeoPoint other) {
		return distance(latitude,
		                longitude,
		                other.latitude,
		                other.longitude);
	}

	public static double distance(double lat1, double lon1, double lat2, double lon2) {

		double a1 = DEG2RAD * lat1;
		double a2 = DEG2RAD * lon1;
		double b1 = DEG2RAD * lat2;
		double b2 = DEG2RAD * lon2;

		double cosa1 = Math.cos(a1);
		double cosb1 = Math.cos(b1);

		double t1 = cosa1 * Math.cos(a2) * cosb1 * Math.cos(b2);
		double t2 = cosa1 * Math.sin(a2) * cosb1 * Math.sin(b2);

		double t3 = Math.sin(a1) * Math.sin(b1);

		// if points are equal t1 + t2 + t3 can be 1.0 + eps
		double tt = Math.acos(Math.min(t1 + t2 + t3, 1.0));

		return (RADIUS_EARTH_METERS * tt);
	}
}
