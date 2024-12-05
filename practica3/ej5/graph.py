import matplotlib.pyplot as plt
import pandas as pd
import seaborn as sns
import numpy as np

# List of CSV files
csv_files = ['unaryTypeA_ST.csv', 'unaryTypeA_MT.csv', 'unaryTypeB_ST.csv', 'unaryTypeB_MT.csv', 'async.csv']
labels = ['Unary A (ST)', 'Unary A (MT)', 'Unary B (ST)', 'Unary B (MT)', 'Async']

# Initialize a color palette
colors = sns.color_palette('husl', len(csv_files))

# Data storage for visualization
bar_width = 0.15  # Width of each bar
group_spacing = 0.5  # Space between groups of (ThreadCount, RepCount)

# Load and process data
all_summaries = []
for idx, file in enumerate(csv_files):
    # Load each CSV file
    data = pd.read_csv(file, header=None, names=['ThreadCount', 'RepCount', 'MeasurementTime'])

    # Convert MeasurementTime to milliseconds for readability
    data['MeasurementTime_ms'] = data['MeasurementTime'] / 1e6

    # Group by ThreadCount and RepCount to calculate mean and std deviation
    grouped = data.groupby(['ThreadCount', 'RepCount'])['MeasurementTime_ms']
    summary = grouped.agg(['mean', 'std']).reset_index()

    # Store file-specific data for plotting
    summary['File'] = labels[idx]
    summary['Color'] = [colors[idx]] * len(summary)  # Assign a consistent color to all rows
    all_summaries.append(summary)

# Combine summaries for all files
combined_summary = pd.concat(all_summaries, ignore_index=True)

# Assign unique indices for (ThreadCount, RepCount) pairs
combined_summary['GroupIndex'] = combined_summary.groupby(['ThreadCount', 'RepCount']).ngroup()
unique_groups = combined_summary[['ThreadCount', 'RepCount', 'GroupIndex']].drop_duplicates()
unique_groups['Label'] = (
    unique_groups['ThreadCount'].astype(str) + ',' + unique_groups['RepCount'].astype(str)
)

# Plot the grouped bar chart
plt.figure(figsize=(20, 12))

# File-specific tendency line data
file_tendency_data = {label: {'x': [], 'y': [], 'color': color} for label, color in zip(labels, colors)}

# Iterate over unique groups for X-axis positions
for group_index, group_data in unique_groups.iterrows():
    x_base = group_data['GroupIndex'] * (len(csv_files) * bar_width + group_spacing)
    for file_index, label in enumerate(labels):
        # Extract data for the current file and group
        subset = combined_summary[
            (combined_summary['GroupIndex'] == group_data['GroupIndex']) &
            (combined_summary['File'] == label)
        ]
        if subset.empty:
            continue

        # Plot average as a bar
        bar = plt.bar(
            x=x_base + file_index * bar_width,  # Position for the current file
            height=subset['mean'],  # Mean values in milliseconds for Y-axis
            width=bar_width,
            color=subset['Color'].iloc[0],
            label=f'{label}' if group_index == 0 else None
        )

        # Add error bars for standard deviation
        plt.errorbar(
            x=x_base + file_index * bar_width,
            y=subset['mean'],
            yerr=subset['std'],
            fmt='none',
            ecolor='black',
            capsize=5
        )

        # Add text for the average value in milliseconds
        for rect in bar:
            avg_ms = subset['mean'].iloc[0]
            y_center = rect.get_height()
            x_center = rect.get_x() + rect.get_width() / 2
            label_position = y_center + (0.05 * y_center if y_center > 0 else 0.1)  # Above if short bar
            plt.text(
                x_center, label_position,
                f'{avg_ms:.1f}ms', ha='center', va='bottom', fontsize=10, color='black'
            )

        # Collect data for the tendency line
        file_tendency_data[label]['x'].append(x_base + file_index * bar_width)
        file_tendency_data[label]['y'].append(subset['mean'].iloc[0])

    # Draw a box around the group
    plt.gca().add_patch(plt.Rectangle(
        (x_base - bar_width / 2, 0),  # Bottom-left corner of the box
        len(labels) * bar_width,  # Box width
        combined_summary['mean'].max() * 1.1,  # Box height
        color='gray',
        alpha=0.2,
        lw=1
    ))

# Plot file-specific tendency lines
for file_data in file_tendency_data.values():
    plt.plot(
        file_data['x'], file_data['y'],
        linestyle='--', marker='o', alpha=0.7, color=file_data['color'],
        linewidth=1.5
    )

# Customize the X-axis
xtick_positions = [
    idx * (len(csv_files) * bar_width + group_spacing) + (len(csv_files) * bar_width / 2)
    for idx in range(len(unique_groups))
]
plt.xticks(xtick_positions, unique_groups['Label'], rotation=45, ha='right')
plt.xlabel('Thread Count, Rep Count')
plt.ylabel('Measurement Time (ms)')

# Simplify the legend (combine mean and std for each file into one entry)
handles, labels = plt.gca().get_legend_handles_labels()
unique_labels = dict(zip(labels, handles))  # Ensure no duplicates
plt.legend(unique_labels.values(), unique_labels.keys(), title='Legend', loc='best')

# Add gridlines for better readability
plt.grid(axis='y', linestyle='--', alpha=0.7)

# Use a logarithmic scale for the Y-axis
plt.yscale('log')

# Add title
plt.title('Measurement Time Analysis with File-Specific Tendencies (Logarithmic Scale)')

plt.tight_layout()

# Show the plot
plt.show()


