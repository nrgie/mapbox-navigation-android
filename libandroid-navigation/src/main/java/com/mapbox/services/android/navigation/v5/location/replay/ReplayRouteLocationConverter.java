package com.mapbox.services.android.navigation.v5.location.replay;

import android.location.Location;

import com.mapbox.api.directions.v5.models.DirectionsRoute;
import com.mapbox.core.constants.Constants;
import com.mapbox.geojson.LineString;
import com.mapbox.geojson.Point;
import com.mapbox.turf.TurfConstants;
import com.mapbox.turf.TurfMeasurement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ReplayRouteLocationConverter {

  private static final int ONE_SECOND = 1;
  private static final int ONE_SECOND_IN_MILLISECONDS = 1000;
  private static final int DEFAULT_DELAY = ONE_SECOND;
  private static final int FORTY_FIVE_KM_PER_HOUR = 45;
  private static final int DEFAULT_SPEED = FORTY_FIVE_KM_PER_HOUR;
  private static final double ONE_KM_IN_METERS = 1000d;
  private static final int ONE_HOUR_IN_SECONDS = 3600;
  private static final String REPLAY_ROUTE = "ReplayRouteLocation";
  private DirectionsRoute route;
  private int speed;
  private int delay;
  private double distance;
  private int currentLeg;
  private int currentStep;
  private long time;

  ReplayRouteLocationConverter(DirectionsRoute route) {
    initialize();
    update(route);
    this.speed = DEFAULT_SPEED;
    this.delay = DEFAULT_DELAY;
    this.distance = calculateDistancePerSec();
  }

  List<Location> toLocations() {
    List<Point> stepPoints = calculateStepPoints();
    List<Location> mockedLocations = calculateMockLocations(stepPoints);

    return mockedLocations;
  }

  void initializeTime() {
    time = System.currentTimeMillis();
  }

  /**
   * Interpolates the route into even points along the route and adds these to the points list.
   *
   * @param lineString our route geometry.
   * @return list of sliced {@link Point}s.
   */
  List<Point> sliceRoute(LineString lineString) {
    double distanceMeters = TurfMeasurement.length(lineString, TurfConstants.UNIT_METERS);
    if (distanceMeters <= 0) {
      return Collections.emptyList();
    }

    List<Point> points = new ArrayList<>();
    for (double i = 0; i < distanceMeters; i += distance) {
      Point point = TurfMeasurement.along(lineString, i, TurfConstants.UNIT_METERS);
      points.add(point);
    }
    return points;
  }

  List<Location> calculateMockLocations(List<Point> points) {
    List<Point> pointsToCopy = new ArrayList<>(points);
    List<Location> mockedLocations = new ArrayList<>();
    for (Point point : points) {
      Location mockedLocation = new Location(REPLAY_ROUTE);
      mockedLocation.setLatitude(point.latitude());
      mockedLocation.setLongitude(point.longitude());

      float speedInMetersPerSec = (float) ((speed * ONE_KM_IN_METERS) / ONE_HOUR_IN_SECONDS);
      mockedLocation.setSpeed(speedInMetersPerSec);

      if (pointsToCopy.size() >= 2) {
        double bearing = TurfMeasurement.bearing(point, points.get(1));
        mockedLocation.setBearing((float) bearing);
      }

      mockedLocation.setAccuracy(3f);
      mockedLocation.setTime(time);
      time += delay * ONE_SECOND_IN_MILLISECONDS;
      mockedLocations.add(mockedLocation);
      pointsToCopy.remove(point);
    }

    return mockedLocations;
  }

  private void update(DirectionsRoute route) {
    this.route = route;
  }

  /**
   * Converts the speed value to m/s and delay to seconds. Then the distance is calculated and returned.
   *
   * @return a double value representing the distance given a speed and time.
   */
  private double calculateDistancePerSec() {
    double distance = (speed * ONE_KM_IN_METERS * delay) / ONE_HOUR_IN_SECONDS;
    return distance;
  }

  private void initialize() {
    this.currentLeg = 0;
    this.currentStep = 0;
  }

  private List<Point> calculateStepPoints() {
    List<Point> stepPoints = new ArrayList<>();

    LineString line = LineString.fromPolyline(
      route.legs().get(currentLeg).steps().get(currentStep).geometry(), Constants.PRECISION_6);
    stepPoints.addAll(sliceRoute(line));
    increaseIndex();

    return stepPoints;
  }

  private void increaseIndex() {
    if (currentStep < route.legs().get(currentLeg).steps().size() - 1) {
      currentStep++;
    } else if (currentLeg < route.legs().size() - 1) {
      currentLeg++;
      currentStep = 0;
    }
  }
}
