export default {
  extends: ['@vben/stylelint-config'],
  overrides: [
    {
      files: ['apps/web-ele/src/**/*.{vue,css,less,scss}'],
      rules: {
        'at-rule-empty-line-before': null,
        'comment-empty-line-before': null,
        'declaration-property-value-keyword-no-deprecated': null,
        'order/properties-order': null,
        'rule-empty-line-before': null,
        'selector-class-pattern': null,
      },
    },
  ],
  root: true,
};
