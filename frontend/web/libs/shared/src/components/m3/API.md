# M3 API cheat-sheet
Import everything from `@pml.tickets/shared` (or `.../components/m3`). Layouts: AppShell, AuthLayout, PublicLayout, Brand. Props shown are the interface fields (see source for full types). Tokens only; no raw px/hex in apps. Table rows are never clickable: use an action column.

## Button
- `Button`: variant?, size?, icon?, danger?, fullWidth?, loading?
- `LinkButton`: variant?, size?, icon?, children?
- `IconButton`: icon, label, variant?, danger?
- `Fab`: icon, label, extended?

## Fields
- `TextField`: label, helperText?, errorText?, prefix?, suffix?, variant?, density?, showCount?, wrapperClassName?
- `TextArea`: label, helperText?, errorText?, variant?, density?, showCount?, wrapperClassName?
- `Select`: label, helperText?, errorText?, variant?, density?, wrapperClassName?
- `Combobox`: label, options, value, onChange, helperText?, errorText?, placeholder?, disabled?, density?, emptyText?, id?
- `DatePicker`
- `TimePicker`
- `Checkbox`: label?, hint?, indeterminate?, 'aria-label'?
- `Radio`: label, hint?
- `RadioGroup`: legend, name, options, value, onChange, errorText?
- `Switch`: label?, hint?
- `ChipInput`: label, values, onChange, placeholder?, helperText?, separators?
- `FileUpload`: label, accept?, multiple?, onFiles, hint?, disabled?, errorText?
- `FormGrid`: children, className?
- `FormCell`: span?, children
- `FormSection`: title?, description?, children, className?

## Display
- `Chip`: kind?, selected?, icon?
- `ChipGroup`: label
- `StatusPill`: tone?, status?
- `SegmentedButton`: label, options, value, onChange
- `Tabs`: label, tabs, value?, defaultValue?, onChange?, sticky?, children?
- `Card`: variant?, flush?, padding?, as?
- `CardHeader`: title, subtitle?, actions?, level?
- `CardFooter`
- `List`
- `ListItem`: leading?, headline, support?, trailing?
- `Divider`
- `KeyValue`: items, columns?
- `SummaryLine`
- `Timeline`: items, label
- `Badge`: count?, dot?, tone?, max?
- `Badged`
- `Avatar`: name, size?, tone?
- `Link`
- `Banner`: tone?, title?, children?, actions?, urgent?
- `EmptyState`: title, description?, icon?, action?
- `ErrorState`: error, onRetry?, onSignIn?, variant?, 'data-testid'?
- `FieldError`
- `Skeleton`: width?, shape?, className?
- `LinearProgress`: value?, label, size?, className?
- `CircularProgress`: value?, label, size?, showValue?
- `Stepper`: steps, current, label?
- `ExpansionItem`: title, subtitle?, trailing?, defaultOpen?, children
- `SaveBar`: message, onSave, onDiscard?, saving?, saveLabel?, hidden?
- `Toolbar`
- `BulkBar`

## Overlays
- `Menu`: trigger, items, align?, label?
- `RowMenu`: label, items
- `Tooltip`: content, children
- `Dialog`: open, onClose, title, children?, actions?, wide?, dismissOnScrim?, alert?
- `ConfirmDialog`: open, onClose, onConfirm, title, description?, confirmLabel?, cancelLabel?, danger?, loading?
- `SideSheet`: open, onClose, title, subtitle?, children?, actions?
- `SnackbarProvider`

## DataTable
- `Pagination`: page, pageSize, total, onPageChange, onPageSizeChange?, pageSizeOptions?, label?
- `DataTable`: caption, columns, rows, getRowId, loading?, skeletonRows?, error?, empty?, sort?, onSortChange?, selectable?, selectedIds?, onSelectionChange?, rowActions?, actionsHeader?, stickyHeader?, density?, pagination?

## Insights
- `KpiCard`: label, value, icon?, trend?, caption?, spark?
- `KpiGrid`
- `StatCard`: label, value, progress?
- `ChartFrame`: title, description?, legend?, table?, children
- `BarChart`: title, description?, data, format?, labelMax?, seriesLabel?
- `LineChart`: title, description?, labels, series, format?
- `DonutChart`: title, description?, data, format?, centre?
- `HorizontalBars`: title, data, format?
- `Sparkline`: values, className?, label?

## Navigation
- `NavigationDrawer`: brand, onCollapse?, header?, primaryAction?, sections, utility?, footer?, linkAs?, modal?, onClose?, label?
- `DrawerCard`
- `NavigationRail`: items, linkAs?, label?, header?, footer?
- `BottomNav`
- `TopAppBar`: title, subtitle?, leading?, actions?, sticky?
- `Breadcrumbs`: items, linkAs?
- `PageHeader`: breadcrumbs?, linkAs?, onBack?, backLabel?
- `NavToggle`

## Storefront
- `HeroBanner`: image, tag?, title, meta?, actions?, footer?
- `SearchBar`: query, onQueryChange, city, onCityChange, cities, when, onWhenChange, whenOptions, onSearch, onMoreFilters?
- `SectionHeader`: eyebrow?, title, description?, actions?, level?
- `EventCard`: title, href, image, date, category, venue, priceFrom?, note?, ribbon?, linkAs?
- `EventGrid`
- `CtaBanner`
- `TierRow`: name, description?, price, available, quantity, onQuantityChange, maxPerOrder?
- `OrderSummary`: title?, lines, totalLabel?, total, note?, action?
- `QrPlaceholder`
- `WalletTicketCard`: eventTitle, dateLine, venue, tier, holder?, code, status?, qr?, actions?

## Console
- `Amount`
- `SettingRow`: label, description?, control
- `QueueItem`: title, meta?, status?, submitted?, actions, leading?
- `ReadinessList`
- `EditableValue`: label, value, onSave, validate?
- `SplitLayout`: main, aside, asideLabel, asideHidden?

## Extra
- `OtpInput`: label?, length?, value, onChange, onComplete?, errorText?, disabled?, autoFocus?
- `Countdown`: until, onExpire?, label?, now?
- `StackedBarChart`: title, description?, labels, series, format?
- `Heatmap`: title, description?, rows, cols, values, format?
- `CommentThread`: label?, comments, onSubmit?, placeholder?, submitLabel?, emptyText?, disabled?
- `HeroCarousel`: label, slides, index?, onIndexChange?
- `PhoneField`: label?, value?, onChange, onCountryChange?, defaultCountry?, helperText?, errorText?, disabled?, id?, name?, density?

## icons
- `Icon`: name, label?

## utils
- `Portal`

## Layouts (`layouts/`)
- `AppShell`: drawer (NavigationDrawerProps minus modal/onClose/onCollapse), bottomNav?, defaultCollapsed?, collapsible?, skipLinkTarget?, linkAs?, overlays?, children. Console shell with skip link, drawer/rail/bottom bar.
- `AuthLayout`: product, console?, title, description?, footer?, children. Centered 400px card with the page h1.
- `SiteHeader`: name, homeHref?, links[{id,label,href,current}], tools?, linkAs?. `SiteTool`: label, children, onClick?, href?. Other storefront pieces (footer, page wrapper) are exported from `PublicLayout.tsx`.
- `Brand`: decorative brand mark.

## Extra (`Extra.tsx`)
- `OtpInput` value/onChange/onComplete, `Countdown` until(ms)/onExpire/label, `StackedBarChart`, `Heatmap`, `CommentThread`, `HeroCarousel`, `PhoneField` (E.164; replaces PhoneNumberInput), `isValidPhone`, `countryForPhone`.
- `SearchableSelect` is replaced by `Combobox`. `SectionError`/`ErrorState` are replaced by `ErrorState`, `EmptyState`, `Banner`.

## Conventions
Provider: `SnackbarProvider` + `useSnackbar()`. Utilities: `cx`, `useControllable`, `useModalBehavior`, `useMediaQuery`, `Portal`. Tokens in TS: `m3Breakpoints`, `m3ChartColors`, `m3HeatColors`, `m3HtmlAttributes`, `m3StatusTone`.
