import React, { useState, useEffect, useRef } from 'react';
import { MapContainer, TileLayer, Polyline, Marker, Popup, useMap } from 'react-leaflet';
import L from 'leaflet';
import axios from 'axios';
import 'leaflet/dist/leaflet.css';
import './LiveDroneTracker.css';

// Fix Leaflet default marker icons
import markerIcon2x from 'leaflet/dist/images/marker-icon-2x.png';
import markerIcon from 'leaflet/dist/images/marker-icon.png';
import markerShadow from 'leaflet/dist/images/marker-shadow.png';

delete L.Icon.Default.prototype._getIconUrl;
L.Icon.Default.mergeOptions({
    iconRetinaUrl: markerIcon2x,
    iconUrl: markerIcon,
    shadowUrl: markerShadow,
});

// Custom drone icon
const droneIcon = new L.Icon({
    iconUrl: 'data:image/svg+xml;base64,' + btoa(`
        <svg xmlns="http://www.w3.org/2000/svg" width="40" height="40" viewBox="0 0 40 40">
            <circle cx="20" cy="20" r="15" fill="#4CAF50" stroke="#2E7D32" stroke-width="2"/>
            <path d="M20 10 L20 30 M10 20 L30 20" stroke="white" stroke-width="2"/>
        </svg>
    `),
    iconSize: [40, 40],
    iconAnchor: [20, 20],
    popupAnchor: [0, -20]
});

// Component to update map view when drone position changes
const MapViewController = ({ position }) => {
    const map = useMap();

    useEffect(() => {
        if (position) {
            map.setView(position, 15);
        }
    }, [position, map]);

    return null;
};

const LiveDroneTracker = ({ droneId, pathData, onTrackingComplete }) => {
    const [currentPosition, setCurrentPosition] = useState(null);
    const [batteryLevel, setBatteryLevel] = useState(100);
    const [status, setStatus] = useState('IDLE');
    const [progress, setProgress] = useState(0);
    const [pathCoordinates, setPathCoordinates] = useState([]);
    const [isTracking, setIsTracking] = useState(false);
    const [totalMoves, setTotalMoves] = useState(0);
    const [currentMove, setCurrentMove] = useState(0);

    const intervalRef = useRef(null);
    const API_BASE = 'http://localhost:8080/api/v1';
    const BATTERY_PER_MOVE = 0.5;
    const MOVE_INTERVAL_MS = 500; // Update every 500ms

    // Extract coordinates from GeoJSON path
    useEffect(() => {
        if (pathData && pathData.dronePaths && pathData.dronePaths.length > 0) {
            const firstPath = pathData.dronePaths[0];
            const geojson = firstPath.geojson;

            if (geojson && geojson.features) {
                const coords = [];
                geojson.features.forEach(feature => {
                    if (feature.geometry && feature.geometry.coordinates) {
                        if (feature.geometry.type === 'LineString') {
                            feature.geometry.coordinates.forEach(coord => {
                                coords.push([coord[1], coord[0]]); // [lat, lng]
                            });
                        }
                    }
                });

                setPathCoordinates(coords);
                setTotalMoves(firstPath.moves || coords.length);

                if (coords.length > 0) {
                    setCurrentPosition(coords[0]);
                }
            }
        }
    }, [pathData]);

    // Start tracking
    const startTracking = async () => {
        if (pathCoordinates.length === 0) return;

        setIsTracking(true);
        setStatus('IN_FLIGHT');
        setCurrentMove(0);
        setProgress(0);

        let moveIndex = 0;
        let battery = batteryLevel;

        intervalRef.current = setInterval(async () => {
            moveIndex++;

            // Calculate progress
            const progressPercent = (moveIndex / pathCoordinates.length) * 100;
            setProgress(progressPercent);
            setCurrentMove(moveIndex);

            // Update position
            if (moveIndex < pathCoordinates.length) {
                setCurrentPosition(pathCoordinates[moveIndex]);

                // Update battery
                battery = Math.max(0, battery - BATTERY_PER_MOVE);
                setBatteryLevel(battery);

                // Update backend cache
                try {
                    await axios.post(`${API_BASE}/drones/status`, {
                        droneId: droneId,
                        status: 'IN_FLIGHT',
                        currentLng: pathCoordinates[moveIndex][1],
                        currentLat: pathCoordinates[moveIndex][0],
                        batteryLevel: battery,
                        currentDeliveryId: droneId // Use drone ID as delivery ID
                    });
                } catch (err) {
                    console.error('Failed to update drone status:', err);
                }
            } else {
                // Reached destination
                clearInterval(intervalRef.current);
                setStatus('RETURNING');

                // Simulate return journey
                setTimeout(() => {
                    handleReturnToBase(battery);
                }, 1000);
            }
        }, MOVE_INTERVAL_MS);
    };

    const handleReturnToBase = async (finalBattery) => {
        setStatus('RETURNING');

        // Simulate return journey (instant for now)
        setTimeout(async () => {
            // Reset to base position (Appleton Tower)
            setCurrentPosition([55.944494, -3.186874]);

            // Determine if charging is needed
            if (finalBattery < 100) {
                setStatus('CHARGING');

                try {
                    await axios.post(`${API_BASE}/drones/status`, {
                        droneId: droneId,
                        status: 'CHARGING',
                        currentLng: -3.186874,
                        currentLat: 55.944494,
                        batteryLevel: finalBattery
                    });

                    // Start charging simulation
                    startCharging(finalBattery);
                } catch (err) {
                    console.error('Failed to start charging:', err);
                }
            } else {
                setStatus('IDLE');
                setIsTracking(false);
                if (onTrackingComplete) onTrackingComplete();
            }
        }, 2000);
    };

    const startCharging = (currentBattery) => {
        let battery = currentBattery;
        const chargingInterval = setInterval(async () => {
            battery = Math.min(100, battery + 2); // Charge 2% per interval
            setBatteryLevel(battery);

            try {
                await axios.post(`${API_BASE}/drones/status`, {
                    droneId: droneId,
                    status: battery >= 100 ? 'IDLE' : 'CHARGING',
                    currentLng: -3.186874,
                    currentLat: 55.944494,
                    batteryLevel: battery
                });
            } catch (err) {
                console.error('Failed to update charging status:', err);
            }

            if (battery >= 100) {
                clearInterval(chargingInterval);
                setStatus('IDLE');
                setIsTracking(false);
                if (onTrackingComplete) onTrackingComplete();
            }
        }, 1000);
    };

    const stopTracking = () => {
        if (intervalRef.current) {
            clearInterval(intervalRef.current);
        }
        setIsTracking(false);
    };

    useEffect(() => {
        return () => {
            if (intervalRef.current) {
                clearInterval(intervalRef.current);
            }
        };
    }, []);

    const getBatteryColor = (level) => {
        if (level > 70) return '#4CAF50';
        if (level > 30) return '#FF9800';
        return '#F44336';
    };

    const getStatusColor = (status) => {
        switch (status) {
            case 'IN_FLIGHT': return '#4CAF50';
            case 'RETURNING': return '#9C27B0';
            case 'CHARGING': return '#FF9800';
            case 'IDLE': return '#2196F3';
            default: return '#757575';
        }
    };

    if (!pathData || pathCoordinates.length === 0) {
        return (
            <div className="live-tracker-empty">
                <p>No path data available for tracking</p>
            </div>
        );
    }

    return (
        <div className="live-drone-tracker">
            <div className="tracker-header">
                <h3>Live Drone Tracking: {droneId}</h3>
                <div className="tracker-controls">
                    {!isTracking && status === 'IDLE' && (
                        <button onClick={startTracking} className="btn-start">
                            Start Flight Simulation
                        </button>
                    )}
                    {isTracking && (
                        <button onClick={stopTracking} className="btn-stop">
                            Stop Tracking
                        </button>
                    )}
                </div>
            </div>

            <div className="tracker-stats">
                <div className="stat-item">
                    <span className="stat-label">Status:</span>
                    <span
                        className="stat-badge"
                        style={{ backgroundColor: getStatusColor(status) }}
                    >
                        {status}
                    </span>
                </div>
                <div className="stat-item">
                    <span className="stat-label">Battery:</span>
                    <div className="battery-display">
                        <div className="battery-bar-outer">
                            <div
                                className="battery-bar-inner"
                                style={{
                                    width: `${batteryLevel}%`,
                                    backgroundColor: getBatteryColor(batteryLevel)
                                }}
                            />
                        </div>
                        <span className="battery-text">{batteryLevel.toFixed(1)}%</span>
                    </div>
                </div>
                <div className="stat-item">
                    <span className="stat-label">Progress:</span>
                    <span className="stat-value">{currentMove} / {totalMoves} moves ({progress.toFixed(0)}%)</span>
                </div>
            </div>

            <div className="tracker-map">
                <MapContainer
                    center={currentPosition || [55.944494, -3.186874]}
                    zoom={15}
                    style={{ height: '500px', width: '100%' }}
                >
                    <TileLayer
                        url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
                        attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
                    />

                    {/* Show full path */}
                    {pathCoordinates.length > 0 && (
                        <Polyline
                            positions={pathCoordinates}
                            color="#3388ff"
                            weight={3}
                            opacity={0.5}
                        />
                    )}

                    {/* Show current position */}
                    {currentPosition && (
                        <Marker position={currentPosition} icon={droneIcon}>
                            <Popup>
                                <div>
                                    <strong>Drone {droneId}</strong><br/>
                                    Status: {status}<br/>
                                    Battery: {batteryLevel.toFixed(1)}%<br/>
                                    Move: {currentMove} / {totalMoves}
                                </div>
                            </Popup>
                        </Marker>
                    )}

                    <MapViewController position={currentPosition} />
                </MapContainer>
            </div>
        </div>
    );
};

export default LiveDroneTracker;
