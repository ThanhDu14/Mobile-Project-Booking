export const ownerFixtures = {
  approvedOwnerA: { uid: 'owner-a', claims: { appRole: 'OWNER' }, profile: { ownerStatus: 'APPROVED', role: 'OWNER', accountStatus: 'ACTIVE' } },
  approvedOwnerB: { uid: 'owner-b', claims: { appRole: 'OWNER' }, profile: { ownerStatus: 'APPROVED', role: 'OWNER', accountStatus: 'ACTIVE' } },
  unapprovedOwner: { uid: 'pending', claims: { appRole: 'OWNER' }, profile: { ownerStatus: 'PENDING', role: 'OWNER', accountStatus: 'ACTIVE' } },
  player: { uid: 'player', claims: { appRole: 'PLAYER' }, profile: { ownerStatus: 'NONE', role: 'PLAYER' } },
  venue: { id: 'venue-a', ownerId: 'owner-a', status: 'ACTIVE' },
  court: { name: 'Sân 1', surfaceType: 'MAT', isActive: true, sortOrder: 0 },
  slots: {
    absent: null,
    held: { status: 'HOLD', blockReason: null },
    booked: { status: 'BOOKED', blockReason: null },
    blocked: { status: 'BLOCKED', blockReason: 'MAINTENANCE' },
  },
} as const;
