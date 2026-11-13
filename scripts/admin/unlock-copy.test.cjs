const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const root = path.resolve(__dirname, '../..');

test('R120-18: unlock guidance states the admin restriction without promising an undefined notification', () => {
  const strings = fs.readFileSync(path.join(root, 'app/src/main/res/values/strings_admin.xml'), 'utf8');
  const copy = strings.match(/<string name="admin_venue_unlock_admin_only_note">([^<]*)<\/string>/)?.[1];
  assert.equal(copy, 'Chỉ admin mở khóa được.');
  const layout = fs.readFileSync(path.join(root, 'app/src/main/res/layout/dialog_admin_unlock_venue.xml'), 'utf8');
  assert.ok(layout.includes('@string/admin_venue_unlock_admin_only_note'));
});
