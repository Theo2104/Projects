export const data = {
    nodes: [
        { id: 'Lukas', name: 'Lukas' },
        { id: 'Tanja', name: 'Tanja' },
        { id: 'Anna', name: 'Anna' },
        { id: 'Ben', name: 'Ben' },
        { id: 'Clara', name: 'Clara' },
        { id: 'David', name: 'David' },
        { id: 'Eva', name: 'Eva' },
        { id: 'Felix', name: 'Felix' },
        { id: 'Greta', name: 'Greta' },
        { id: 'Hannah', name: 'Hannah' }
    ],
    links: [
        { source: 'Lukas', target: 'Anna' },
        { source: 'Anna', target: 'Tanja' },
        { source: 'Lukas', target: 'Ben' },
        { source: 'Ben', target: 'Clara' },
        { source: 'Clara', target: 'Tanja' },
        { source: 'Lukas', target: 'David' },
        { source: 'David', target: 'Eva' },
        { source: 'David', target: 'Felix' },
        { source: 'Felix', target: 'Greta' },
        { source: 'Greta', target: 'Hannah' },
        { source: 'Hannah', target: 'Tanja' },
        { source: 'Lukas', target: 'Greta' },
        { source: 'Ben', target: 'David' },
        { source: 'Anna', target: 'Felix' },
        { source: 'Eva', target: 'Hannah' },
        { source: 'Hannah', target: 'Ben' },
        { source: 'Felix', target: 'Clara' },
        { source: 'Tanja', target: 'Greta' },
        { source: 'Clara', target: 'David' },
        { source: 'Eva', target: 'Ben' }
    ]
};

