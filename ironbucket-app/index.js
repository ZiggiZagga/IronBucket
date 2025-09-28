const express = require('express');

const app = express();
app.use(express.json());

// Placeholder authentication endpoint
app.post('/auth', (req, res) => {
  // This will be extended for Keycloak and passkey support
  res.status(200).json({ message: 'Auth endpoint reachable' });
});

const PORT = process.env.PORT || 3000;
if (process.env.NODE_ENV !== 'test') {
  app.listen(PORT, () => {
    console.log(`Server running on port ${PORT}`);
  });
}

module.exports = app;
