import React from 'react';

const DeliveryResult = ({ result }) => {
    if (!result) return null;

    return (
        <div className="delivery-result">
            <h3>Delivery Scheduled!</h3>
            <div className="result-details">
                <p><strong>Total Cost:</strong> £{result.cost.toFixed(2)}</p>
                <p><strong>Total Moves:</strong> {result.moves}</p>
                <p><strong>Drones Used:</strong> {result.dronePaths.length}</p>

                {result.dronePaths.map((path, idx) => (
                    <div key={idx} className="drone-path-summary">
                        <h4>Drone {path.droneId}</h4>
                        <p>Deliveries: {path.deliveries.length}</p>
                        <p>Moves: {path.moves}</p>
                        <p>Cost: £{path.cost.toFixed(2)}</p>
                    </div>
                ))}
            </div>
        </div>
    );
};

export default DeliveryResult;