import React, { useState, useEffect } from 'react';
import axios from 'axios';
import './DroneDashboard.css';

const DroneDashboard = () => {
    const [drones, setDrones] = useState([]);
    const [stats, setStats] = useState({});
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    const API_BASE = 'http://localhost:8080/api/v1';

    const fetchDrones = async () => {
        try {
            const response = await axios.get(`${API_BASE}/drones/active`);
            setDrones(response.data);
            setError(null);
        } catch (err) {
            console.error('Error fetching drones:', err);
            setError('Failed to fetch drone data');
        }
    };

    const fetchStats = async () => {
        try {
            const response = await axios.get(`${API_BASE}/drones/stats`);
            setStats(response.data);
        } catch (err) {
            console.error('Error fetching stats:', err);
        }
    };

    useEffect(() => {
        const loadData = async () => {
            setLoading(true);
            await Promise.all([fetchDrones(), fetchStats()]);
            setLoading(false);
        };
        loadData();
    }, []);

    useEffect(() => {
        const interval = setInterval(() => {
            fetchDrones();
            fetchStats();
        }, 5000);

        return () => clearInterval(interval);
    }, []);

    // Automatic charging for drones with CHARGING status
    useEffect(() => {
        const chargingInterval = setInterval(async () => {
            // Find all drones that are charging
            const chargingDrones = drones.filter(d => d.status === 'CHARGING' && d.batteryLevel < 100);

            for (const drone of chargingDrones) {
                try {
                    const newBattery = Math.min(100, drone.batteryLevel + 2); // Charge 2% per second
                    const newStatus = newBattery >= 100 ? 'IDLE' : 'CHARGING';

                    await axios.post(`${API_BASE}/drones/status`, {
                        droneId: drone.droneId,
                        status: newStatus,
                        currentLng: drone.currentLng,
                        currentLat: drone.currentLat,
                        batteryLevel: newBattery
                    });
                } catch (err) {
                    console.error('Error auto-charging drone:', err);
                }
            }

            // Refresh if any drones were charging
            if (chargingDrones.length > 0) {
                fetchDrones();
            }
        }, 1000); // Check every second

        return () => clearInterval(chargingInterval);
    }, [drones]);

    const getStatusColor = (status) => {
        switch (status) {
            case 'IN_FLIGHT': return '#4CAF50';
            case 'IDLE': return '#2196F3';
            case 'CHARGING': return '#FF9800';
            case 'MAINTENANCE': return '#F44336';
            case 'RETURNING': return '#9C27B0';
            default: return '#757575';
        }
    };

    const getBatteryColor = (level) => {
        if (level > 70) return '#4CAF50';
        if (level > 30) return '#FF9800';
        return '#F44336';
    };

    const handleCompleteDelivery = async (droneId) => {
        try {
            const response = await axios.post(`${API_BASE}/completeDelivery/${droneId}`);
            console.log('Delivery completed:', response.data);
            await fetchDrones(); // Refresh the list
        } catch (err) {
            console.error('Error completing delivery:', err);
            setError('Failed to complete delivery: ' + (err.response?.data?.error || err.message));
        }
    };


    const handleSyncDrones = async () => {
        setLoading(true);
        setError(null);
        try {
            const response = await axios.post(`${API_BASE}/syncDronesToCache`);
            console.log('Sync response:', response.data);
            await fetchDrones(); // Refresh the list
            setError(null);
            alert(`Successfully synced ${response.data.synced} drones to cache!`);
        } catch (err) {
            console.error('Error syncing drones:', err);
            setError('Failed to sync drones: ' + (err.response?.data?.error || err.message) + '. Make sure the ILP REST service is running!');
        } finally {
            setLoading(false);
        }
    };

    if (loading) {
        return (
            <div className="dashboard-loading">
                <div className="spinner"></div>
                <p>Loading drone data...</p>
            </div>
        );
    }

    return (
        <div className="drone-dashboard">
            <header className="dashboard-header">
                <div className="header-top">
                    <h1>Drone Fleet Monitor</h1>
                    <button onClick={handleSyncDrones} className="sync-button" disabled={loading}>
                        🔄 Sync Drones from ILP
                    </button>
                </div>
                <div className="header-stats">
                    <div className="stat-card">
                        <span className="stat-label">Active Drones</span>
                        <span className="stat-value">{stats.activeDrones || 0}</span>
                    </div>
                    <div className="stat-card">
                        <span className="stat-label">In Flight</span>
                        <span className="stat-value">{stats.inFlight || 0}</span>
                    </div>
                    <div className="stat-card">
                        <span className="stat-label">Idle</span>
                        <span className="stat-value">{stats.idle || 0}</span>
                    </div>
                </div>
            </header>

            {error && (
                <div className="error-banner">
                    {error}
                </div>
            )}

            <div className="drone-grid">
                {drones.length === 0 ? (
                    <div className="no-drones">
                        <p>No active drones</p>
                        <small>Create a drone using the Simulator tab</small>
                    </div>
                ) : (
                    drones.map((drone) => (
                        <div key={drone.droneId} className="drone-card">
                            <div className="drone-header">
                                <h3>{drone.droneId}</h3>
                                <span
                                    className="status-badge"
                                    style={{ backgroundColor: getStatusColor(drone.status) }}
                                >
                  {drone.status}
                </span>
                            </div>

                            <div className="drone-details">
                                <div className="detail-row">
                                    <span className="detail-label">Location</span>
                                    <span className="detail-value">
                    {drone.currentLng?.toFixed(4)}, {drone.currentLat?.toFixed(4)}
                  </span>
                                </div>

                                <div className="detail-row">
                                    <span className="detail-label">Battery</span>
                                    <div className="battery-bar">
                                        <div
                                            className="battery-fill"
                                            style={{
                                                width: `${drone.batteryLevel || 0}%`,
                                                backgroundColor: getBatteryColor(drone.batteryLevel)
                                            }}
                                        ></div>
                                        <span className="battery-text">{drone.batteryLevel?.toFixed(1)}%</span>
                                    </div>
                                </div>

                                {drone.currentDeliveryId && (
                                    <div className="detail-row">
                                        <span className="detail-label">Delivery ID</span>
                                        <span className="detail-value">{drone.currentDeliveryId}</span>
                                    </div>
                                )}

                                <div className="detail-row">
                                    <span className="detail-label">Last Update</span>
                                    <span className="detail-value time">
                    {new Date(drone.lastUpdate).toLocaleTimeString()}
                  </span>
                                </div>
                            </div>

                        </div>
                    ))
                )}
            </div>
        </div>
    );
};

export default DroneDashboard;