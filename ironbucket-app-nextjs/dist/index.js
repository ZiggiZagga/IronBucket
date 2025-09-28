"use strict";
var __importDefault = (this && this.__importDefault) || function (mod) {
    return (mod && mod.__esModule) ? mod : { "default": mod };
};
Object.defineProperty(exports, "__esModule", { value: true });
const express_1 = __importDefault(require("express"));
const app = (0, express_1.default)();
app.use(express_1.default.json());
// Placeholder authentication endpoint
app.post('/auth', (req, res) => {
    res.status(200).json({ message: 'Auth endpoint reachable' });
});
const PORT = process.env.PORT || 3000;
if (process.env.NODE_ENV !== 'test') {
    app.listen(PORT, () => {
        console.log(`Server running on port ${PORT}`);
    });
}
exports.default = app;
