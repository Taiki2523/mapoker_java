export type BlindFormat = {
  label: string
  smallBlind: number
  bigBlind: number
  category: string
}

export const BLIND_FORMATS: BlindFormat[] = [
  { category: 'ローステークス', label: '1 / 2',    smallBlind: 1,    bigBlind: 2    },
  { category: 'ローステークス', label: '2 / 4',    smallBlind: 2,    bigBlind: 4    },
  { category: 'ローステークス', label: '2 / 5',    smallBlind: 2,    bigBlind: 5    },
  { category: 'ローステークス', label: '3 / 6',    smallBlind: 3,    bigBlind: 6    },
  { category: 'ローステークス', label: '5 / 10',   smallBlind: 5,    bigBlind: 10   },
  { category: 'ミドルステークス', label: '10 / 20',  smallBlind: 10,   bigBlind: 20   },
  { category: 'ミドルステークス', label: '25 / 50',  smallBlind: 25,   bigBlind: 50   },
  { category: 'ミドルステークス', label: '50 / 100', smallBlind: 50,   bigBlind: 100  },
  { category: 'ハイステークス', label: '100 / 200', smallBlind: 100,  bigBlind: 200  },
  { category: 'ハイステークス', label: '200 / 400', smallBlind: 200,  bigBlind: 400  },
  { category: 'ハイステークス', label: '500 / 1000',smallBlind: 500,  bigBlind: 1000 },
  { category: 'ハイステークス', label: '1000 / 2000',smallBlind: 1000, bigBlind: 2000 },
]

export const BLIND_FORMAT_CATEGORIES = [...new Set(BLIND_FORMATS.map((f) => f.category))]
