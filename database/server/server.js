const express = require('express');
const fs = require('fs');
const csv = require('csv-parser');
const cors = require('cors');
const app = express();
const port = 5000;

app.use(cors());
app.get('/api/csv-data', (req, res) => {
  const results = [];
  
  fs.createReadStream('worldData.csv')
    .pipe(csv())
    .on('data', (data) => results.push(data))
    .on('end', () => {
      res.json(results);
    });
});

app.listen(port, () => {
  console.log(`Server is running on http://localhost:${port}`);
});
