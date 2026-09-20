const https = require('https');

https.get('https://raw.githubusercontent.com/search?q=IncrementalSplitterRunnable+failure', (resp) => {
  let data = '';
  resp.on('data', (chunk) => { data += chunk; });
  resp.on('end', () => { console.log(data.substring(0, 500)); });
}).on("error", (err) => { console.log("Error: " + err.message); });
