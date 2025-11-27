import React, { useState } from 'react';
import axios from 'axios';
import './DeliveryForm.css';
import MapPicker from "./MapPicker";



const DeliveryForm = () => {
    const [formData, setFormData] = useState({
        date: '',
        time: '',
        capacity: '',
        cooling: false,
        heating: false,
        maxCost: '',
        longitude: '',
        latitude: ''
    });

    const [loading, setLoading] = useState(false);
    const [error, setError] = useState(null);
    const [success, setSuccess] = useState(false);

    const [mapPosition, setMapPosition] = useState(null);

    const handleMapClick = (latlng) => {
        setMapPosition([latlng.lat, latlng.lng]);
        setFormData(prev => ({
            ...prev,
            latitude: latlng.lat.toFixed(6),
            longitude: latlng.lng.toFixed(6)
        }));
    };

    const handleChange = (e) => {
        const { name, value, type, checked } = e.target;
        setFormData(prev => ({
            ...prev,
            [name]: type === 'checkbox' ? checked : value
        }));
    };

    const handleSubmit = async (e) => {
        e.preventDefault();
        setLoading(true);
        setError(null);
        setSuccess(false);

        try {
            // Transform form data to MedDispatchRec format
            const medDispatchRec = {
                date: formData.date,
                time: formData.time,
                requirements: {
                    capacity: formData.capacity ? parseFloat(formData.capacity) : null,
                    cooling: formData.cooling,
                    heating: formData.heating,
                    maxCost: formData.maxCost ? parseFloat(formData.maxCost) : null
                },
                delivery: {
                    lng: parseFloat(formData.longitude),
                    lat: parseFloat(formData.latitude)
                }
            };

            // Send to your Spring Boot backend
            const response = await axios.post(
                'http://localhost:8080/api/deliveries',
                [medDispatchRec], // Send as array
                {
                    headers: {
                        'Content-Type': 'application/json'
                    }
                }
            );

            console.log('Response:', response.data);
            setSuccess(true);

            // Reset form
            setFormData({
                date: '',
                time: '',
                capacity: '',
                cooling: false,
                heating: false,
                maxCost: '',
                longitude: '',
                latitude: ''
            });

        } catch (err) {
            console.error('Error submitting delivery:', err);
            setError(err.response?.data?.message || 'Failed to submit delivery');
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="delivery-form-container">
            <h2>📦 Schedule Drone Delivery</h2>

            {success && (
                <div className="alert alert-success">
                    ✅ Delivery scheduled successfully!
                </div>
            )}

            {error && (
                <div className="alert alert-error">
                    ❌ {error}
                </div>
            )}

            <form onSubmit={handleSubmit} className="delivery-form">
                {/* Date & Time Section */}
                <div className="form-section">
                    <h3>📅 Schedule</h3>

                    <div className="form-group">
                        <label htmlFor="date">Delivery Date *</label>
                        <input
                            type="date"
                            id="date"
                            name="date"
                            value={formData.date}
                            onChange={handleChange}
                            required
                            min={new Date().toISOString().split('T')[0]}
                        />
                    </div>

                    <div className="form-group">
                        <label htmlFor="time">Delivery Time *</label>
                        <input
                            type="time"
                            id="time"
                            name="time"
                            value={formData.time}
                            onChange={handleChange}
                            required
                        />
                    </div>
                </div>

                <div className="form-section">
                    <h3>🗺️ Select Location on Map</h3>
                    <p style={{ fontSize: '0.9rem', color: '#666', marginBottom: '1rem' }}>
                        Click on the map to select delivery location
                    </p>
                    <MapPicker
                        position={mapPosition}
                        onLocationSelect={handleMapClick}
                    />
                </div>

                {/* Location Section */}
                <div className="form-section">
                    <h3>📍 Delivery Location</h3>

                    <div className="form-group">
                        <label htmlFor="longitude">Longitude *</label>
                        <input
                            type="number"
                            id="longitude"
                            name="longitude"
                            value={formData.longitude}
                            onChange={handleChange}
                            step="0.000001"
                            placeholder="-3.188267"
                            required
                        />
                    </div>

                    <div className="form-group">
                        <label htmlFor="latitude">Latitude *</label>
                        <input
                            type="number"
                            id="latitude"
                            name="latitude"
                            value={formData.latitude}
                            onChange={handleChange}
                            step="0.000001"
                            placeholder="55.944251"
                            required
                        />
                    </div>
                </div>

                {/* Requirements Section */}
                <div className="form-section">
                    <h3>📋 Requirements</h3>

                    <div className="form-group">
                        <label htmlFor="capacity">Cargo Capacity (kg)</label>
                        <input
                            type="number"
                            id="capacity"
                            name="capacity"
                            value={formData.capacity}
                            onChange={handleChange}
                            step="0.1"
                            min="0"
                            placeholder="1.0"
                        />
                    </div>

                    <div className="form-group">
                        <label htmlFor="maxCost">Maximum Cost (£)</label>
                        <input
                            type="number"
                            id="maxCost"
                            name="maxCost"
                            value={formData.maxCost}
                            onChange={handleChange}
                            step="0.01"
                            min="0"
                            placeholder="12.00"
                        />
                    </div>

                    <div className="form-group-checkbox">
                        <label>
                            <input
                                type="checkbox"
                                name="cooling"
                                checked={formData.cooling}
                                onChange={handleChange}
                            />
                            ❄️ Requires Cooling
                        </label>
                    </div>

                    <div className="form-group-checkbox">
                        <label>
                            <input
                                type="checkbox"
                                name="heating"
                                checked={formData.heating}
                                onChange={handleChange}
                            />
                            🔥 Requires Heating
                        </label>
                    </div>
                </div>

                <button
                    type="submit"
                    className="submit-button"
                    disabled={loading}
                >
                    {loading ? '⏳ Processing...' : '🚁 Schedule Delivery'}
                </button>
            </form>
        </div>
    );
};

export default DeliveryForm;