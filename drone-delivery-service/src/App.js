import React, { useState } from 'react';
import DroneDashboard from './components/DroneDashboard';
import DeliveryForm from './components/DeliveryForm';
import './App.css';

function App() {
    const [activeTab, setActiveTab] = useState('dashboard');

    return (
        <div className="App">
            <nav className="app-nav">
                <h1>Drone Management System</h1>
                <div className="nav-tabs">
                    <button
                        className={activeTab === 'dashboard' ? 'active' : ''}
                        onClick={() => setActiveTab('dashboard')}
                    >
                        Dashboard
                    </button>
                    <button
                        className={activeTab === 'delivery' ? 'active' : ''}
                        onClick={() => setActiveTab('delivery')}
                    >
                        Deliveries
                    </button>
                </div>
            </nav>

            <main>
                {activeTab === 'dashboard' && <DroneDashboard />}
                {activeTab === 'delivery' && <DeliveryForm />}
            </main>
        </div>
    );
}

export default App;