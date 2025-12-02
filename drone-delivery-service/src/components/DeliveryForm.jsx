import React, { useState, useEffect } from 'react';
import axios from 'axios';
import { MapContainer, TileLayer, Marker, useMapEvents, Popup } from 'react-leaflet';
import L from 'leaflet';
import 'leaflet/dist/leaflet.css';
import PathMapViewer from './PathMapViewer';
import LiveDroneTracker from './LiveDroneTracker';
import './DeliveryForm.css';

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

const MapClickHandler = ({ onMapClick }) => {
    useMapEvents({
        click(e) {
            onMapClick(e.latlng);
        },
    });
    return null;
};

const DeliveryForm = () => {
    const [formData, setFormData] = useState({
        orderId: '',
        date: new Date().toISOString().split('T')[0],
        time: '12:00',
        servicePointLng: -3.1883,
        servicePointLat: 55.9533,
        patientName: '',
        medication: '',
        requiresCooling: false,
        requiresHeating: false,
        capacity: 1.0
    });

    const [servicePoints, setServicePoints] = useState([]);
    const [pathResult, setPathResult] = useState(null);
    const [message, setMessage] = useState(null);
    const [loading, setLoading] = useState(false);
    const [showMap, setShowMap] = useState(false);
    const [assignedDroneId, setAssignedDroneId] = useState(null);
    const [showLiveTracking, setShowLiveTracking] = useState(false);

    const API_BASE = 'http://localhost:8080/api/v1';

    // Fetch service points on mount
    useEffect(() => {
        fetchServicePoints();
    }, []);

    const fetchServicePoints = async () => {
        try {
            const response = await axios.get(`${API_BASE}/service-points`);
            setServicePoints(response.data);
            console.log('Fetched service points:', response.data);
        } catch (err) {
            console.error('Error fetching service points:', err);
        }
    };

    const handleMapClick = (latlng) => {
        setFormData(prev => ({
            ...prev,
            servicePointLng: latlng.lng,
            servicePointLat: latlng.lat
        }));
    };

    const handleSubmit = async (e) => {
        e.preventDefault();

        if (!formData.orderId.trim()) {
            setMessage({ type: 'error', text: 'Please enter an Order ID' });
            return;
        }

        setLoading(true);
        setMessage(null);

        try {
            // Prepare the dispatch request in the format your MedDispatchRec expects
            // Only include requirements if cooling/heating is needed or capacity != 1
            let requirements = null;
            if (formData.requiresCooling || formData.requiresHeating || formData.capacity !== 1.0) {
                requirements = {
                    capacity: formData.capacity,
                    cooling: formData.requiresCooling,
                    heating: formData.requiresHeating,
                    maxCost: null
                };
            }

            const dispatchRequest = {
                id: parseInt(formData.orderId) || 1, // Must be Integer
                date: formData.date,
                time: formData.time,
                requirements: requirements,
                delivery: {
                    lng: formData.servicePointLng,
                    lat: formData.servicePointLat,
                    alt: 0 // LngLatAlt requires altitude
                }
            };

            console.log('Submitting delivery request:', dispatchRequest);

            // Call the existing endpoint in DroneController
            const response = await axios.post(
                `${API_BASE}/calcDeliveryPath`,
                [dispatchRequest] // Wrap in array - endpoint expects List
            );

            console.log('Path calculation response:', response.data);

            // Check if we got a valid response
            if (!response.data || !response.data.dronePaths || response.data.dronePaths.length === 0) {
                setMessage({
                    type: 'error',
                    text: 'No available drones found for this delivery. Check: (1) ILP REST service is running, (2) Date/time matches drone availability, (3) Drone capabilities match requirements'
                });
                setPathResult(null);
                return;
            }

            // Extract assigned drone ID and flight path
            const droneId = response.data.dronePaths?.[0]?.droneId;

            // Transform backend response to include geojson for PathMapViewer
            const transformedData = {
                ...response.data,
                dronePaths: response.data.dronePaths.map(dronePath => {
                    // Extract all flightPath coordinates from all deliveries
                    const allCoordinates = [];
                    if (dronePath.deliveries) {
                        dronePath.deliveries.forEach(delivery => {
                            if (delivery.flightPath) {
                                delivery.flightPath.forEach(point => {
                                    allCoordinates.push([point.lng, point.lat]); // [lng, lat] for GeoJSON
                                });
                            }
                        });
                    }

                    // Create GeoJSON structure for PathMapViewer
                    const geojson = {
                        type: "FeatureCollection",
                        features: [{
                            type: "Feature",
                            properties: {},
                            geometry: {
                                type: "LineString",
                                coordinates: allCoordinates
                            }
                        }]
                    };

                    return {
                        droneId: dronePath.droneId,
                        geojson: geojson,
                        moves: dronePath.moves,
                        cost: dronePath.cost
                    };
                })
            };

            if (droneId && response.data.dronePaths[0].deliveries) {
                // Extract coordinates for backend simulation (lat, lng order)
                const allCoordinates = [];
                response.data.dronePaths[0].deliveries.forEach(delivery => {
                    if (delivery.flightPath) {
                        delivery.flightPath.forEach(point => {
                            allCoordinates.push([point.lat, point.lng]); // [lat, lng] for backend
                        });
                    }
                });

                // Start the backend simulation
                try {
                    await axios.post(`${API_BASE}/startSimulation`, {
                        droneId: droneId,
                        deliveryId: String(formData.orderId),
                        pathCoordinates: allCoordinates
                    });

                    setMessage({
                        type: 'success',
                        text: `Delivery started! Drone ${droneId} is now flying. Check the path below and switch to Dashboard to watch live progress. Total moves: ${response.data.totalMoves || 'N/A'}, Cost: ${response.data.totalCost?.toFixed(2) || 'N/A'}`
                    });
                } catch (simErr) {
                    console.error('Failed to start simulation:', simErr);
                    setMessage({
                        type: 'success',
                        text: `Delivery scheduled! Drone: ${droneId}, Total moves: ${response.data.totalMoves || 'N/A'}, Cost: ${response.data.totalCost?.toFixed(2) || 'N/A'}. (Simulation failed to start: ${simErr.message})`
                    });
                }
            } else {
                setMessage({
                    type: 'success',
                    text: `Delivery scheduled! Total moves: ${response.data.totalMoves || 'N/A'}, Cost: ${response.data.totalCost?.toFixed(2) || 'N/A'}`
                });
            }

            setPathResult(transformedData);
            setAssignedDroneId(droneId);
            setShowLiveTracking(false); // Show static path map instead of live tracking

            // Reset form
            setFormData({
                orderId: '',
                date: new Date().toISOString().split('T')[0],
                time: '12:00',
                servicePointLng: -3.1883,
                servicePointLat: 55.9533,
                patientName: '',
                medication: '',
                requiresCooling: false,
                requiresHeating: false,
                capacity: 1.0
            });

        } catch (err) {
            console.error('Delivery scheduling error:', err);
            console.error('Error response:', err.response?.data);

            const errorMessage = err.response?.data?.message
                || err.response?.data?.error
                || err.message
                || 'Unknown error occurred';

            setMessage({
                type: 'error',
                text: `Failed to schedule delivery: ${errorMessage}`
            });
            setPathResult(null);
        } finally {
            setLoading(false);
        }
    };

    const handleInputChange = (e) => {
        const { name, value } = e.target;
        setFormData(prev => ({
            ...prev,
            [name]: value
        }));
    };

    return (
        <div className="delivery-form-container">
            <div className="delivery-form">
                <h2>Schedule Medical Delivery</h2>

                {message && (
                    <div className={`message ${message.type}`}>
                        {message.text}
                    </div>
                )}

                <form onSubmit={handleSubmit}>
                    {/* Order ID */}
                    <div className="form-group">
                        <label>Order ID (Number) *</label>
                        <input
                            type="number"
                            name="orderId"
                            placeholder="e.g., 1001"
                            value={formData.orderId}
                            onChange={handleInputChange}
                            required
                        />
                    </div>

                    {/* Date and Time */}
                    <div className="form-row">
                        <div className="form-group">
                            <label>Delivery Date *</label>
                            <input
                                type="date"
                                name="date"
                                value={formData.date}
                                onChange={handleInputChange}
                                required
                            />
                        </div>

                        <div className="form-group">
                            <label>Delivery Time *</label>
                            <input
                                type="time"
                                name="time"
                                value={formData.time}
                                onChange={handleInputChange}
                                required
                            />
                        </div>
                    </div>

                    {/* Service Point Location */}
                    <div className="form-group">
                        <div className="section-header">
                            <label>Destination Service Point *</label>
                            <button
                                type="button"
                                className={`map-toggle ${showMap ? 'active' : ''}`}
                                onClick={() => setShowMap(!showMap)}
                            >
                                {showMap ? 'Click map to set location' : 'Select on Map'}
                            </button>
                        </div>

                        <div className="coordinates">
                            <div className="form-group">
                                <label>Longitude</label>
                                <input
                                    type="number"
                                    step="0.000001"
                                    name="servicePointLng"
                                    value={formData.servicePointLng}
                                    onChange={handleInputChange}
                                />
                            </div>
                            <div className="form-group">
                                <label>Latitude</label>
                                <input
                                    type="number"
                                    step="0.000001"
                                    name="servicePointLat"
                                    value={formData.servicePointLat}
                                    onChange={handleInputChange}
                                />
                            </div>
                        </div>

                        {showMap && (
                            <div className="map-picker">
                                <MapContainer
                                    center={[formData.servicePointLat, formData.servicePointLng]}
                                    zoom={13}
                                    style={{ height: '400px', width: '100%' }}
                                >
                                    <TileLayer
                                        url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
                                        attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
                                    />

                                    {/* Show existing service points */}
                                    {servicePoints.map(sp => (
                                        <Marker
                                            key={sp.id}
                                            position={[sp.location.lat, sp.location.lng]}
                                        >
                                            <Popup>{sp.name}</Popup>
                                        </Marker>
                                    ))}

                                    {/* Show selected location */}
                                    <Marker position={[formData.servicePointLat, formData.servicePointLng]}>
                                        <Popup>Selected Destination</Popup>
                                    </Marker>

                                    <MapClickHandler onMapClick={handleMapClick} />
                                </MapContainer>
                            </div>
                        )}
                    </div>

                    {/* Delivery Requirements */}
                    <div className="form-group">
                        <label>Delivery Requirements</label>
                        <div className="requirements-section">
                            <div className="checkbox-group">
                                <label className="checkbox-label">
                                    <input
                                        type="checkbox"
                                        name="requiresCooling"
                                        checked={formData.requiresCooling}
                                        onChange={(e) => setFormData(prev => ({
                                            ...prev,
                                            requiresCooling: e.target.checked
                                        }))}
                                    />
                                    <span>Cooling Required</span>
                                </label>
                            </div>
                            <div className="checkbox-group">
                                <label className="checkbox-label">
                                    <input
                                        type="checkbox"
                                        name="requiresHeating"
                                        checked={formData.requiresHeating}
                                        onChange={(e) => setFormData(prev => ({
                                            ...prev,
                                            requiresHeating: e.target.checked
                                        }))}
                                    />
                                    <span>Heating Required</span>
                                </label>
                            </div>
                            <div className="form-group-inline">
                                <label>Capacity (kg)</label>
                                <input
                                    type="number"
                                    step="0.1"
                                    name="capacity"
                                    value={formData.capacity}
                                    onChange={handleInputChange}
                                    min="0.1"
                                    max="10"
                                />
                            </div>
                        </div>
                    </div>

                    {/* Optional Fields */}
                    <div className="form-group">
                        <label>Patient Name (Optional)</label>
                        <input
                            type="text"
                            name="patientName"
                            placeholder="e.g., John Smith"
                            value={formData.patientName}
                            onChange={handleInputChange}
                        />
                    </div>

                    <div className="form-group">
                        <label>Medication (Optional)</label>
                        <input
                            type="text"
                            name="medication"
                            placeholder="e.g., Insulin"
                            value={formData.medication}
                            onChange={handleInputChange}
                        />
                    </div>

                    {/* Submit Button */}
                    <button
                        type="submit"
                        className="submit-button"
                        disabled={loading}
                    >
                        {loading ? 'Calculating Path...' : 'Schedule Delivery'}
                    </button>
                </form>

                {/* Display Path Result and Live Tracking */}
                {pathResult && showLiveTracking && assignedDroneId && (
                    <LiveDroneTracker
                        droneId={assignedDroneId}
                        pathData={pathResult}
                        onTrackingComplete={() => {
                            console.log('Tracking complete');
                            setShowLiveTracking(false);
                        }}
                    />
                )}

                {pathResult && !showLiveTracking && (
                    <PathMapViewer pathData={pathResult} />
                )}
            </div>
        </div>
    );
};

export default DeliveryForm;