// Expected copy comes from the compiled Java catalog in the guarded preview, never a second hand-written list.
const fs = require('node:fs');
const path = require('node:path');
const review = fs.readFileSync(path.join(__dirname, '../build/exhibition-live/text-preview-latest.log'), 'utf8');
const line = review.split(/\r?\n/).find(line => line.startsWith('Text catalog: '));
if (!line) throw new Error('Run TextPreview to export the compiled catalog before verifying the exhibition');
module.exports = {
  catalog: JSON.parse(line.slice('Text catalog: '.length)),
  journal: JSON.parse(fs.readFileSync(path.join(__dirname, '../local-data/exhibition-20261031/exhibition.json'), 'utf8')),
};
