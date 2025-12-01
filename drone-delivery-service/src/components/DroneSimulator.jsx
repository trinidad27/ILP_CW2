import React, { useState } from 'react';
import axios from 'axios';
import './DroneSimulator.css';

const DroneSimulator = () => {
    const [droneId, setDroneId] = useState('');
    const [status, setStatus] = useState('IDLE');
    const [batteryLevel, setBatteryLevel] = useState(100);
    const [loading, setLoading] = useState(false);
    const [message, setMessage] = useState(null);

    const API_BASE = 'http://localhost:8080/api/v1';

    const statuses = ['IDLE', 'IN_FLIGHT', 'DELIVERING', 'RETURNING', 'CHARGING', 'MAINTENANCE'];

    const generateRandomLocation = () => {
        const baseLng = -3.188267;
        const baseLat = 55.944251;
        const randomLng = baseLng + (Math.random() - 0.5) * 0.1;
        const randomLat = baseLat + (Math.random() - 0.5) * 0.1;
        return { lng: randomLng, lat: randomLat };
    };

    const createDrone = async () => {
        if (!droneId.trim()) {
            setMessage({ type: 'error', text: 'Please enter a drone ID' });
            return;
        }

        setLoading(true);
        try {
            const location = generateRandomLocation();
            const droneData = {
                droneId: droneId,
                status: status,
                currentLng: location.lng,
                currentLat: location.lat,
                batteryLevel: batteryLevel,
                speed: status === 'IN_FLIGHT' ? Math.random() * 20 + 5 : 0,
                altitude: status === 'IN_FLIGHT' ? Math.random() * 100 + 50 : 0
            };

            await axios.post(`${API_BASE}/drones/status`, droneData);

            setMessage({ type: 'success', text: `Drone ${droneId} created successfully!` });
            setDroneId('');

        } catch (err) {
            setMessage({ type: 'error', text: 'Failed to create drone' });
        } finally {
            setLoading(false);
        }
    };

    const createRandomFleet = async () => {
        setLoading(true);
        try {
            const fleetSize = 5;
            for (let i = 1; i <= fleetSize; i++) {
                const location = generateRandomLocation();
                const randomStatus = statuses[Math.floor(Math.random() * statuses.length)];
                const droneData = {
                    droneId: `DRONE-${String(i).padStart(3, '0')}`,
                    status: randomStatus,
                    currentLng: location.lng,
                    currentLat: location.lat,
                    batteryLevel: Math.random() * 40 + 60,
                    speed: randomStatus === 'IN_FLIGHT' ? Math.random() * 20 + 5 : 0,
                    altitude: randomStatus === 'IN_FLIGHT' ? Math.random() * 100 + 50 : 0
                };

                await axios.post(`${API_BASE}/drones/status`, droneData);
                await new Promise(resolve => setTimeout(resolve, 200));
            }

            setMessage({ type: 'success', text: `Created ${fleetSize} drones successfully!` });
        } catch (err) {
            setMessage({ type: 'error', text: 'Failed to create fleet' });
        } finally {
            setLoading(false);
        }
    };

    const clearAllDrones = async () => {
        if (!window.confirm('Clear all drones from cache?')) return;

        setLoading(true);
        try {
            await axios.delete(`${API_BASE}/drones/cache/clear`);
            setMessage({ type: 'success', text: 'All drones cleared successfully!' });
        } catch (err) {
            setMessage({ type: 'error', text: 'Failed to clear cache' });
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="drone-simulator">
            <h2>Drone Simulator</h2>

            {message && (
                <div className={`message ${message.type}`}>
                    {message.text}
                </div>
            )}

            <div className="simulator-controls">
                <div className="form-group">
                    <label>Drone ID</label>
                    <input
                        type="text"
                        value={droneId}
                        onChange={(e) => setDroneId(e.target.value)}
                        placeholder="DRONE-001"
                    />
                </div>

                <div className="form-group">
                    <label>Status</label>
                    <select value={status} onChange={(e) => setStatus(e.target.value)}>
                        {statuses.map(s => (
                            <option key={s} value={s}>{s}</option>
                        ))}
                    </select>
                </div>

                <div className="form-group">
                    <label>Battery Level: {batteryLevel}%</label>
                    <input
                        type="range"
                        min="0"
                        max="100"
                        value={batteryLevel}
                        onChange={(e) => setBatteryLevel(Number(e.target.value))}
                    />
                </div>

                <button
                    onClick={createDrone}
                    disabled={loading}
                    className="btn-primary"
                >
                    {loading ? 'Creating...' : 'Create Drone'}
                </button>

                <button
                    onClick={createRandomFleet}
                    disabled={loading}
                    className="btn-secondary"
                >
                    {loading ? 'Creating...' : 'Create Fleet (5 drones)'}
                </button>

                <button
                    onClick={clearAllDrones}
                    disabled={loading}
                    className="btn-danger"
                >
                    Clear All
                </button>
            </div>
        </div>
    );
};

export default DroneSimulator;